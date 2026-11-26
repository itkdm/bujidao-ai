package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
    void shouldThrowInvalidClientWhenAuthorizeRateLimited() {
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE);
        when(authorizationService.approveOrDeny(eq("code"), eq(CLIENT_ID), eq("mcp:access"),
                eq(REDIRECT_URI), eq(true), eq("state-1"), eq("challenge-1"), eq("S256"),
                eq(RESOURCE), eq(RESOURCE))).thenReturn("code-xxx");

        CommonResult<String> first = approve(request());
        assertThat(first.getData()).isEqualTo("code-xxx");

        // 同一 IP 第二次授权同意：超限抛 OAuth invalid_client（400），且不调用 Service。
        // 注意：本单测只锁定控制器抛出的异常本身；异常出控制器后由 GlobalExceptionHandler
        // 接手，而它目前没有 McpOAuthException 分支，会落到兜底返回 CommonResult(500)，
        // 所以 HTTP 层面的“不 500”需生产侧接住该异常后才能成立，不在本测试覆盖范围。
        assertThatThrownBy(() -> approve(request()))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
        verify(authorizationService, times(1)).approveOrDeny(eq("code"), eq(CLIENT_ID),
                eq("mcp:access"), eq(REDIRECT_URI), eq(true), eq("state-1"), eq("challenge-1"),
                eq("S256"), eq(RESOURCE), eq(RESOURCE));
    }

}
