package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthTokenExchangeService;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private ResponseEntity<?> postToken(MockHttpServletRequest request, String grantType) {
        if ("authorization_code".equals(grantType)) {
            return controller.postAccessToken(request, "authorization_code", "mcp-dcr-abc",
                    "code-123", null, null, null, null);
        }
        return controller.postAccessToken(request, "refresh_token", "mcp-dcr-abc",
                null, null, null, null, "refresh-token-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"authorization_code", "refresh_token"})
    void shouldReturnStandardOAuthErrorWhenTokenRateLimited(String grantType) {
        if ("authorization_code".equals(grantType)) {
            when(tokenExchangeService.exchangeAuthorizationCode(eq("mcp-dcr-abc"), eq("code-123"),
                    isNull(), isNull(), isNull(), any())).thenReturn(new OAuth2OpenAccessTokenRespVO());
        } else {
            when(tokenExchangeService.refreshAccessToken("mcp-dcr-abc", "refresh-token-1"))
                    .thenReturn(new OAuth2OpenAccessTokenRespVO());
        }

        ResponseEntity<?> first = postToken(request(), grantType);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 成功响应也带 Cache-Control: no-store（OAuth 令牌响应不应被缓存）
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");

        // 同一 IP 第二次兑换：超限，不进 Service，直接返回标准 OAuth JSON 错误
        // （429 + temporarily_unavailable + Retry-After: 60，与 60 秒限流窗口一致）
        ResponseEntity<?> limited = postToken(request(), grantType);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) limited.getBody();
        assertThat(body.get("error")).isEqualTo("temporarily_unavailable");
        assertThat(body.get("error_description")).asString().contains("too many requests");
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(limited.getHeaders().getCacheControl()).isEqualTo("no-store");
        if ("authorization_code".equals(grantType)) {
            verify(tokenExchangeService, times(1)).exchangeAuthorizationCode(eq("mcp-dcr-abc"),
                    eq("code-123"), isNull(), isNull(), isNull(), any());
        } else {
            verify(tokenExchangeService, times(1))
                    .refreshAccessToken("mcp-dcr-abc", "refresh-token-1");
        }
    }

    @Test
    void shouldExchangeWithoutRateLimiterWhenNotInjected() {
        // rateLimiter 字段为 null（手工 new 出来的 Controller，没走 Spring 注入）时不限流，
        // 仍能正常兑换：checkRateLimit 的 null 兜底不能把正常请求挡掉
        McpOAuthTokenController noLimiterController = new McpOAuthTokenController();
        ReflectionTestUtils.setField(noLimiterController, "tokenExchangeService", tokenExchangeService);
        ReflectionTestUtils.setField(noLimiterController, "resourceUriResolver", resourceUriResolver);
        when(tokenExchangeService.refreshAccessToken("mcp-dcr-abc", "refresh-token-1"))
                .thenReturn(new OAuth2OpenAccessTokenRespVO());

        ResponseEntity<?> response = noLimiterController.postAccessToken(request(), "refresh_token",
                "mcp-dcr-abc", null, null, null, null, "refresh-token-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldNotRateLimitRevoke() {
        when(tokenExchangeService.refreshAccessToken("mcp-dcr-abc", "refresh-token-1"))
                .thenReturn(new OAuth2OpenAccessTokenRespVO());
        when(tokenExchangeService.revokeToken("mcp-dcr-abc", "access-token-1")).thenReturn(true);

        // 先把 token 限流打满
        assertThat(postToken(request(), "refresh_token").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postToken(request(), "refresh_token").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // 吊销是安全操作，不限流：超限后仍能连续吊销
        for (int i = 0; i < 3; i++) {
            ResponseEntity<?> response = controller.revokeToken("mcp-dcr-abc", "access-token-1");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(Map.of());
        }
        verify(tokenExchangeService, times(3)).revokeToken("mcp-dcr-abc", "access-token-1");
    }

}
