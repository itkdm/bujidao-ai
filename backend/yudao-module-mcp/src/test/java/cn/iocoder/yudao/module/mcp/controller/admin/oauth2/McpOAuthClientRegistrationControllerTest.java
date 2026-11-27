package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationReqVO;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationRespVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthClientRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthClientRegistrationControllerTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String RESOURCE_URI = "http://127.0.0.1:48080/mcp";

    @Mock
    private McpOAuthClientRegistrationService clientRegistrationService;
    @Mock
    private McpOAuthResourceUriResolver resourceUriResolver;

    private McpOAuthClientRegistrationController controller;
    private McpOAuthRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setRegisterPerIpPerMinute(1);
        rateLimiter = new McpOAuthRateLimiter(config);
        controller = new McpOAuthClientRegistrationController();
        ReflectionTestUtils.setField(controller, "clientRegistrationService", clientRegistrationService);
        ReflectionTestUtils.setField(controller, "resourceUriResolver", resourceUriResolver);
        ReflectionTestUtils.setField(controller, "rateLimiter", rateLimiter);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        return request;
    }

    private static McpOAuthClientRegistrationReqVO reqVO() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setClientName("WorkBuddy");
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        return reqVO;
    }

    private static McpOAuthClientRegistrationRespVO respVO() {
        return new McpOAuthClientRegistrationRespVO()
                .setClientId("mcp-dcr-abc")
                .setClientIdIssuedAt(1792000000L)
                .setClientName("WorkBuddy")
                .setRedirectUris(List.of("http://127.0.0.1:10244/callback"))
                .setGrantTypes(List.of("authorization_code"))
                .setResponseTypes(List.of("code"))
                .setTokenEndpointAuthMethod("none")
                .setApplicationType("native")
                .setScope("mcp:access");
    }

    @Test
    void shouldReturnCreatedOnSuccess() {
        McpOAuthClientRegistrationReqVO reqVO = reqVO();
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE_URI);
        when(clientRegistrationService.register(eq(reqVO), eq(RESOURCE_URI))).thenReturn(respVO());

        ResponseEntity<?> response = controller.register(request(), reqVO);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(respVO());
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        verify(clientRegistrationService).register(eq(reqVO), eq(RESOURCE_URI));
    }

    @Test
    void shouldReturn429WhenRegisterRateLimited() {
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE_URI);
        when(clientRegistrationService.register(any(), anyString())).thenReturn(respVO());

        ResponseEntity<?> first = controller.register(request(), reqVO());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 同一 IP 第二次注册：限流在 service 之前拦截，不调 service。
        ResponseEntity<?> second = controller.register(request(), reqVO());
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) second.getBody();
        assertThat(body).containsEntry("error", "temporarily_unavailable");
        verify(clientRegistrationService, times(1)).register(any(), anyString());
    }

    @Test
    void shouldReturn404WhenDcrDisabled() {
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE_URI);
        when(clientRegistrationService.register(any(), anyString()))
                .thenThrow(new McpOAuthException(HttpStatus.NOT_FOUND,
                        "invalid_request", "Dynamic client registration is disabled"));

        ResponseEntity<?> response = controller.register(request(), reqVO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("error", "invalid_request");
    }

    @Test
    void shouldReturn400WhenRedirectUriInvalid() {
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE_URI);
        when(clientRegistrationService.register(any(), anyString()))
                .thenThrow(new McpOAuthException(HttpStatus.BAD_REQUEST,
                        "invalid_redirect_uri", "redirect_uris contains unsupported URI"));

        ResponseEntity<?> response = controller.register(request(), reqVO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("error", "invalid_redirect_uri");
    }

    @Test
    void shouldAllowWhenRateLimiterAbsent() {
        // 手工 new 出来的 Controller 没有 rateLimiter 时不限流（与 authorize/token 控制器同一约定）。
        McpOAuthClientRegistrationController noLimiterController = new McpOAuthClientRegistrationController();
        ReflectionTestUtils.setField(noLimiterController, "clientRegistrationService", clientRegistrationService);
        ReflectionTestUtils.setField(noLimiterController, "resourceUriResolver", resourceUriResolver);
        when(resourceUriResolver.resolve(any())).thenReturn(RESOURCE_URI);
        when(clientRegistrationService.register(any(), anyString())).thenReturn(respVO());

        ResponseEntity<?> response = noLimiterController.register(request(), reqVO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

}
