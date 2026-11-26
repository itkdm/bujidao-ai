package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2CodeService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthTokenExchangeServiceTest {

    private static final String CLIENT_ID_URL = "https://client.example.com/app";
    private static final String REDIRECT_URI = "https://client.example.com/callback";
    private static final String RESOURCE = "https://mcp.example.com/mcp";
    private static final String CODE_VERIFIER = "test-code-verifier-0123456789abcdef";

    @Mock
    private OAuth2ClientService oauth2ClientService;
    @Mock
    private OAuth2CodeService oauth2CodeService;
    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private OAuth2RefreshTokenMapper oauth2RefreshTokenMapper;
    @Mock
    private McpOAuthAuthorizationCodeExtraRedisDAO codeExtraRedisDAO;
    @Mock
    private McpOAuthClientResolver clientResolver;

    private McpOAuthTokenExchangeService service;

    @BeforeEach
    void setUp() {
        service = new McpOAuthTokenExchangeService(oauth2ClientService, oauth2CodeService,
                oauth2TokenService, oauth2RefreshTokenMapper, codeExtraRedisDAO, clientResolver);
    }

    @Test
    void shouldExchangeAuthorizationCodeForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());
        when(oauth2TokenService.createAccessToken(eq(1L), eq(2), eq(CLIENT_ID_URL), eq(List.of("mcp:access"))))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE);

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        assertThat(respVO.getScope()).isEqualTo("mcp:access");
        verify(codeExtraRedisDAO).delete("code-123");
        verifyNoInteractions(oauth2ClientService);
    }

    @Test
    void shouldFailExchangeWhenCimdDocumentIsInvalid() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null))
                .thenThrow(McpOAuthException.invalidClient("client metadata document is invalid (FETCH_FAILED)"));

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
        verify(oauth2CodeService, never()).consumeAuthorizationCode(anyString());
    }

    @Test
    void shouldExchangeAuthorizationCodeForDbClientUnchanged() {
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache("mcp-dcr-abc", null,
                "authorization_code", null, null)).thenReturn(dbClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtraDb());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDODb());
        when(oauth2TokenService.createAccessToken(eq(1L), eq(2), eq("mcp-dcr-abc"), eq(List.of("mcp:access"))))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.exchangeAuthorizationCode(
                "mcp-dcr-abc", "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE);

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        verify(clientResolver, never()).resolve(anyString(), any());
    }

    @Test
    void shouldRefreshAccessTokenForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                        .setClientId(CLIENT_ID_URL).setTenantId(1L));
        when(oauth2TokenService.refreshAccessToken("refresh-token-1", CLIENT_ID_URL))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.refreshAccessToken(CLIENT_ID_URL, "refresh-token-1");

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        verifyNoInteractions(oauth2ClientService);
    }

    @Test
    void shouldRejectRefreshWhenGrantTypeNotAllowedForCimdClient() {
        OAuth2ClientDO client = cimdClient().setAuthorizedGrantTypes(List.of("authorization_code"));
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);

        assertThatThrownBy(() -> service.refreshAccessToken(CLIENT_ID_URL, "refresh-token-1"))
                .isInstanceOf(ServiceException.class)
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(1_002_020_003);
        verify(oauth2RefreshTokenMapper, never()).selectByRefreshToken(anyString());
    }

    @Test
    void shouldRevokeTokenForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(oauth2TokenService.getAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                        .setClientId(CLIENT_ID_URL));
        when(oauth2TokenService.removeAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1"));

        assertThat(service.revokeToken(CLIENT_ID_URL, "access-token-1")).isTrue();
    }

    @Test
    void shouldNotRevokeTokenForOtherClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(oauth2TokenService.getAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                        .setClientId("mcp-dcr-other"));

        assertThat(service.revokeToken(CLIENT_ID_URL, "access-token-1")).isFalse();
        verify(oauth2TokenService, never()).removeAccessToken(anyString());
    }

    private static OAuth2ClientDO cimdClient() {
        return new OAuth2ClientDO().setClientId(CLIENT_ID_URL).setSecret("").setName("Test App")
                .setLogo("https://client.example.com/logo.png").setStatus(0)
                .setRedirectUris(List.of(REDIRECT_URI))
                .setAuthorizedGrantTypes(List.of("authorization_code", "refresh_token"))
                .setScopes(List.of("mcp:access"))
                .setAdditionalInformation("{\"token_endpoint_auth_method\":\"none\","
                        + "\"public_client\":true,\"require_pkce\":true}");
    }

    private static OAuth2ClientDO dbClient() {
        return cimdClient().setClientId("mcp-dcr-abc");
    }

    private static McpOAuthAuthorizationCodeExtraDO codeExtra() {
        return new McpOAuthAuthorizationCodeExtraDO().setCode("code-123").setClientId(CLIENT_ID_URL)
                .setRedirectUri(REDIRECT_URI).setCodeChallenge(s256Challenge(CODE_VERIFIER))
                .setCodeChallengeMethod("S256").setResource(RESOURCE).setUserId(1L).setUserType(2)
                .setTenantId(null).setExpiresTime(LocalDateTime.now().plusSeconds(300));
    }

    private static McpOAuthAuthorizationCodeExtraDO codeExtraDb() {
        return codeExtra().setClientId("mcp-dcr-abc");
    }

    private static OAuth2CodeDO codeDO() {
        return new OAuth2CodeDO().setCode("code-123").setUserId(1L).setUserType(2)
                .setClientId(CLIENT_ID_URL).setScopes(List.of("mcp:access")).setRedirectUri(REDIRECT_URI)
                .setExpiresTime(LocalDateTime.now().plusSeconds(300));
    }

    private static OAuth2CodeDO codeDODb() {
        return codeDO().setClientId("mcp-dcr-abc");
    }

    private static OAuth2AccessTokenDO accessTokenDO() {
        return new OAuth2AccessTokenDO().setAccessToken("access-token-1").setScopes(List.of("mcp:access"))
                .setExpiresTime(LocalDateTime.now().plusSeconds(1800));
    }

    private static String s256Challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
