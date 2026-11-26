package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthTokenExchangeService;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthTokenControllerTest {

    private static final String CLIENT_IP = "203.0.113.7";

    @Mock
    private McpOAuthTokenExchangeService tokenExchangeService;
    @Mock
    private McpOAuthResourceUriResolver resourceUriResolver;

    private McpOAuthTokenController controller;
    private McpOAuthRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setTokenPerIpPerMinute(1);
        rateLimiter = new McpOAuthRateLimiter(config);
        controller = new McpOAuthTokenController();
        ReflectionTestUtils.setField(controller, "tokenExchangeService", tokenExchangeService);
        ReflectionTestUtils.setField(controller, "resourceUriResolver", resourceUriResolver);
        ReflectionTestUtils.setField(controller, "rateLimiter", rateLimiter);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        return request;
    }

    private ResponseEntity<?> postToken(MockHttpServletRequest request) {
        return controller.postAccessToken(request, "refresh_token", "mcp-dcr-abc",
                null, null, null, null, "refresh-token-1");
    }

    @Test
    void shouldReturnStandardOAuthErrorWhenTokenRateLimited() {
        when(tokenExchangeService.refreshAccessToken("mcp-dcr-abc", "refresh-token-1"))
                .thenReturn(new OAuth2OpenAccessTokenRespVO());

        ResponseEntity<?> first = postToken(request());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 同一 IP 第二次兑换：超限，不进 Service，直接返回标准 OAuth JSON 错误
        // （429 + temporarily_unavailable + Retry-After: 60，与 60 秒限流窗口一致）
        ResponseEntity<?> limited = postToken(request());
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) limited.getBody();
        assertThat(body.get("error")).isEqualTo("temporarily_unavailable");
        assertThat(body.get("error_description")).asString().contains("too many requests");
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(limited.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(tokenExchangeService, times(1))
                .refreshAccessToken("mcp-dcr-abc", "refresh-token-1");
    }

    @Test
    void shouldNotRateLimitRevoke() {
        when(tokenExchangeService.refreshAccessToken("mcp-dcr-abc", "refresh-token-1"))
                .thenReturn(new OAuth2OpenAccessTokenRespVO());
        when(tokenExchangeService.revokeToken("mcp-dcr-abc", "access-token-1")).thenReturn(true);

        // 先把 token 限流打满
        assertThat(postToken(request()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postToken(request()).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // 吊销是安全操作，不限流：超限后仍能连续吊销
        for (int i = 0; i < 3; i++) {
            ResponseEntity<?> response = controller.revokeToken("mcp-dcr-abc", "access-token-1");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(Map.of());
        }
        verify(tokenExchangeService, times(3)).revokeToken("mcp-dcr-abc", "access-token-1");
    }

}
