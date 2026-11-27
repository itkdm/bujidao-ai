package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthTokenExchangeService;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    static Stream<Arguments> serviceExceptionMappings() {
        return Stream.of(
                Arguments.of(ErrorCodeConstants.OAUTH2_CLIENT_NOT_EXISTS.getCode(), "invalid_client"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CLIENT_DISABLE.getCode(), "invalid_client"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CLIENT_CLIENT_SECRET_ERROR.getCode(), "invalid_client"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CLIENT_REDIRECT_URI_NOT_MATCH.getCode(), "invalid_grant"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS.getCode(),
                        "unauthorized_client"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CLIENT_SCOPE_OVER.getCode(), "invalid_scope"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CODE_NOT_EXISTS.getCode(), "invalid_grant"),
                Arguments.of(ErrorCodeConstants.OAUTH2_CODE_EXPIRE.getCode(), "invalid_grant"),
                Arguments.of(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "invalid_grant"),
                Arguments.of(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "invalid_grant"),
                Arguments.of(999_999_999, "invalid_request"));
    }

    @ParameterizedTest
    @MethodSource("serviceExceptionMappings")
    void shouldMapServiceExceptionToStandardOAuthError(int code, String expectedError) {
        // 上游 ServiceException 不能再漏出成 HTTP 200 + CommonResult：按 RFC 6749 §5.2
        // 映射成 400 + 标准 OAuth JSON（error / error_description），且带 no-store。
        when(tokenExchangeService.refreshAccessToken("mcp-dcr-abc", "refresh-token-1"))
                .thenThrow(new ServiceException(code, "test error"));

        ResponseEntity<?> response = postToken(request(), "refresh_token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo(expectedError);
        assertThat(body.get("error_description")).isEqualTo("test error");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void shouldReturnInvalidRequestWhenGrantTypeMissing() {
        // grant_type 缺失：不再依赖 Spring 的参数缺失异常（会变成 HTTP 200 + CommonResult），
        // 而是在方法内按 OAuth 形状返回 400 invalid_request，且不进 Service。
        ResponseEntity<?> response = controller.postAccessToken(request(), null,
                "mcp-dcr-abc", null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("invalid_request");
        assertThat(body.get("error_description")).asString().contains("grant_type is required");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verifyNoInteractions(tokenExchangeService);
    }

    @Test
    void shouldReturnInvalidRequestWhenRevokeTokenMissing() {
        ResponseEntity<?> response = controller.revokeToken("mcp-dcr-abc", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("invalid_request");
        assertThat(body.get("error_description")).asString().contains("token is required");
        verify(tokenExchangeService, times(0)).revokeToken(any(), any());
    }

    @Test
    void shouldMapServiceExceptionOnRevoke() {
        // revoke 端点同理：DB 分支 validatePublicClient 抛出的 ServiceException
        // 也要映射成标准 OAuth JSON，而不是 HTTP 200 + CommonResult。
        when(tokenExchangeService.revokeToken("mcp-dcr-abc", "access-token-1"))
                .thenThrow(new ServiceException(ErrorCodeConstants.OAUTH2_CLIENT_NOT_EXISTS));

        ResponseEntity<?> response = controller.revokeToken("mcp-dcr-abc", "access-token-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("invalid_client");
        assertThat(body.get("error_description")).isEqualTo("OAuth2 客户端不存在");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void shouldMapServiceExceptionOnAuthorizationCodeBranch() {
        // 映射函数是两分支共用的：授权码分支 consumeAuthorizationCode 抛出的
        // CODE_NOT_EXISTS 也要同样映成 invalid_grant。
        when(tokenExchangeService.exchangeAuthorizationCode(eq("mcp-dcr-abc"), eq("code-123"),
                isNull(), isNull(), isNull(), any()))
                .thenThrow(new ServiceException(ErrorCodeConstants.OAUTH2_CODE_NOT_EXISTS));

        ResponseEntity<?> response = postToken(request(), "authorization_code");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("invalid_grant");
        assertThat(body.get("error_description")).isEqualTo("code 不存在");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldReturnInvalidRequestWhenGrantTypeBlank(String grantType) {
        // 空串 / 空白串与 null 同样是缺失（StrUtil.isBlank），不应进 Service。
        ResponseEntity<?> response = controller.postAccessToken(request(), grantType,
                "mcp-dcr-abc", null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("invalid_request");
        verifyNoInteractions(tokenExchangeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldReturnInvalidRequestWhenRevokeTokenBlank(String token) {
        ResponseEntity<?> response = controller.revokeToken("mcp-dcr-abc", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("invalid_request");
        verify(tokenExchangeService, times(0)).revokeToken(any(), any());
    }

}
