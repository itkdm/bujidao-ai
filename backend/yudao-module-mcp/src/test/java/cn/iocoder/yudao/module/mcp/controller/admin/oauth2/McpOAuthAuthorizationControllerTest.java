package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthAuthorizationControllerTest {

    private static final String CLIENT_ID = "https://client.example.com/app";
    private static final String REDIRECT_URI = "https://client.example.com/callback";
    private static final String RESOURCE = "https://mcp.example.com/mcp";

    @Mock
    private McpOAuthAuthorizationService authorizationService;
    @Mock
    private McpOAuthResourceUriResolver resourceUriResolver;

    private McpOAuthAuthorizationController controller;

    @BeforeEach
    void setUp() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setAuthorizePerIpPerMinute(1);
        controller = new McpOAuthAuthorizationController();
        ReflectionTestUtils.setField(controller, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(controller, "resourceUriResolver", resourceUriResolver);
        ReflectionTestUtils.setField(controller, "rateLimiter", new McpOAuthRateLimiter(config));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    private CommonResult<String> approve(MockHttpServletRequest request) {
        return controller.approveOrDeny(request, "code", CLIENT_ID, "mcp:access", REDIRECT_URI,
                true, "state-1", "challenge-1", "S256", RESOURCE);
    }

    @Test
    void shouldThrowTooManyRequestsWhenAuthorizeRateLimited() {
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE);
        when(authorizationService.approveOrDeny(eq("code"), eq(CLIENT_ID), eq("mcp:access"),
                eq(REDIRECT_URI), eq(true), eq("state-1"), eq("challenge-1"), eq("S256"),
                eq(RESOURCE), eq(RESOURCE))).thenReturn("code-xxx");

        CommonResult<String> first = approve(request());
        assertThat(first.getData()).isEqualTo("code-xxx");

        // 同一 IP 第二次授权同意：超限抛 ServiceException(TOO_MANY_REQUESTS, 429)，且不调用 Service。
        // 注意：本单测只锁定控制器抛出的异常本身；异常出控制器后由 GlobalExceptionHandler
        // 的 serviceExceptionHandler 分支接手，转成 body code 429 的 CommonResult，
        // HTTP 状态仍为 200（芋道通用约定：无 @ResponseStatus、也不返回 ResponseEntity）。
        assertThatThrownBy(() -> approve(request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage(GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getMsg())
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(GlobalErrorCodeConstants.TOO_MANY_REQUESTS.getCode());
        verify(authorizationService, times(1)).approveOrDeny(eq("code"), eq(CLIENT_ID),
                eq("mcp:access"), eq(REDIRECT_URI), eq(true), eq("state-1"), eq("challenge-1"),
                eq("S256"), eq(RESOURCE), eq(RESOURCE));
    }

    @Test
    void shouldApproveWithoutRateLimiterWhenNotInjected() {
        // rateLimiter 字段为 null（手工 new 出来的 Controller，没走 Spring 注入）时不限流，
        // 仍能正常授权：checkRateLimit 的 null 兜底不能把正常请求挡掉
        McpOAuthAuthorizationController noLimiterController = new McpOAuthAuthorizationController();
        ReflectionTestUtils.setField(noLimiterController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(noLimiterController, "resourceUriResolver", resourceUriResolver);
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE);
        when(authorizationService.approveOrDeny(eq("code"), eq(CLIENT_ID), eq("mcp:access"),
                eq(REDIRECT_URI), eq(true), eq("state-1"), eq("challenge-1"), eq("S256"),
                eq(RESOURCE), eq(RESOURCE))).thenReturn("code-xxx");

        CommonResult<String> result = noLimiterController.approveOrDeny(request(), "code", CLIENT_ID,
                "mcp:access", REDIRECT_URI, true, "state-1", "challenge-1", "S256", RESOURCE);

        assertThat(result.getData()).isEqualTo("code-xxx");
    }

}
