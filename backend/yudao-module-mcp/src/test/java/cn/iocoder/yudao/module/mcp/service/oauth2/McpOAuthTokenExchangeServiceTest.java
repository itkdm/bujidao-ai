package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    private OAuth2AccessTokenMapper oauth2AccessTokenMapper;
    @Mock
    private OAuth2AccessTokenRedisDAO oauth2AccessTokenRedisDAO;
    @Mock
    private McpOAuthAuthorizationCodeExtraRedisDAO codeExtraRedisDAO;
    @Mock
    private McpOAuthClientResolver clientResolver;

    private McpOAuthTokenExchangeService service;

    @BeforeEach
    void setUp() {
        service = new McpOAuthTokenExchangeService(oauth2ClientService, oauth2CodeService,
                oauth2TokenService, oauth2RefreshTokenMapper, oauth2AccessTokenMapper,
                oauth2AccessTokenRedisDAO, codeExtraRedisDAO, clientResolver,
                new McpOAuthProperties());
    }

    @Test
    void shouldExchangeAuthorizationCodeForCimdClient() {
        OAuth2ClientDO client = cimdClient();
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());
        // CIMD 客户端走上游 OAuth2ClientDO 重载（不查库），不用 String 重载
        when(oauth2TokenService.createAccessToken(eq(1L), eq(2), eq(client), eq(List.of("mcp:access"))))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE);

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        assertThat(respVO.getScope()).isEqualTo("mcp:access");
        verify(codeExtraRedisDAO).delete("code-123");
        verifyNoInteractions(oauth2ClientService);
        // 明确走 clientDO 重载：解析出的临时客户端原样传给上游，不经过 DB 查库
        verify(oauth2TokenService).createAccessToken(eq(1L), eq(2), eq(client), eq(List.of("mcp:access")));
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
        OAuth2ClientDO client = cimdClient();
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId(CLIENT_ID_URL);
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        // CIMD 客户端走上游 OAuth2ClientDO 重载（不查库），不用 String 重载
        when(oauth2TokenService.refreshAccessToken("refresh-token-1", client))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.refreshAccessToken(CLIENT_ID_URL, "refresh-token-1");

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        verifyNoInteractions(oauth2ClientService);
        // 明确走 clientDO 重载：解析出的临时客户端原样传给上游，不经过 DB 查库
        verify(oauth2TokenService).refreshAccessToken("refresh-token-1", client);
    }

    @Test
    void shouldRejectRefreshWhenGrantTypeNotAllowedForCimdClient() {
        OAuth2ClientDO client = cimdClient().setAuthorizedGrantTypes(List.of("authorization_code"));
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        // 刷新先查令牌再解析客户端：令牌匹配才能走到 grant 校验
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId(CLIENT_ID_URL);
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);

        assertThatThrownBy(() -> service.refreshAccessToken(CLIENT_ID_URL, "refresh-token-1"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(oauth2TokenService, never()).refreshAccessToken(eq("refresh-token-1"), eq(client));
    }

    @Test
    void shouldRejectExchangeWhenGrantTypeNotAllowedForCimdClient() {
        OAuth2ClientDO client = cimdClient().setAuthorizedGrantTypes(List.of("refresh_token"));
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(oauth2CodeService, never()).consumeAuthorizationCode(anyString());
    }

    @Test
    void shouldRejectExchangeWhenRedirectUriNotRegisteredForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", "https://client.example.com/other",
                CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(oauth2CodeService, never()).consumeAuthorizationCode(anyString());
    }

    @Test
    void shouldRejectExchangeWhenPkceFailsForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, "wrong-verifier", RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
    }

    @Test
    void shouldRejectExchangeWhenResourceMismatchesForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER,
                "https://other.example.com/mcp", RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(codeExtraRedisDAO, never()).get(anyString());
    }

    @Test
    void shouldExchangeWhenResourceOmittedOnBothSides() {
        // 授权与兑换都不带 resource：兑换步 resource 比对（null == null）通过，换码成功。
        OAuth2ClientDO client = cimdClient();
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra().setResource(null));
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());
        when(oauth2TokenService.createAccessToken(eq(1L), eq(2), eq(client), eq(List.of("mcp:access"))))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, null, null);

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        verify(codeExtraRedisDAO).delete("code-123");
    }

    @Test
    void shouldRejectExchangeWhenResourceOmittedButAuthorizedWithResource() {
        // 授权带 R、兑换不带：兑换步 resource 比对（null != R）判 invalid_grant，
        // 带与不带的 resource 混不过去。注意授权码已在校验前被消费（P1-2），
        // 重试须重新授权；codeExtra 未删、随 Redis TTL 过期。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, null, null))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(oauth2CodeService).consumeAuthorizationCode("code-123");
        verify(codeExtraRedisDAO, never()).delete(anyString());
    }

    @Test
    void shouldRejectExchangeWhenResourceAddedButAuthorizedWithoutResource() {
        // 授权不带、兑换带 R：兑换步 matches() 先放行合规的 R（与注册/current 比对），
        // 真正拦住它的是 validateAuthorizationCode（R != null）判 invalid_grant。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra().setResource(null));
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(oauth2CodeService).consumeAuthorizationCode("code-123");
        verify(codeExtraRedisDAO, never()).delete(anyString());
    }

    @Test
    void shouldFailExchangeWhenCodeExtraIsMissing() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(null);

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(oauth2CodeService, never()).consumeAuthorizationCode(anyString());
    }

    @Test
    void shouldFailExchangeWhenConsumeAuthorizationCodeReturnsNull() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(null);

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verify(codeExtraRedisDAO, never()).delete(anyString());
    }

    @Test
    void shouldExchangeAuthorizationCodeWhenAdditionalInformationUsesCamelTokenEndpointAuthMethod() {
        // isPublicClient 的驼峰 key 回退：tokenEndpointAuthMethod（驼峰）也要认
        OAuth2ClientDO client = cimdClient()
                .setAdditionalInformation("{\"tokenEndpointAuthMethod\":\"none\"}");
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());
        when(oauth2TokenService.createAccessToken(eq(1L), eq(2), eq(client), eq(List.of("mcp:access"))))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE);

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
    }

    @Test
    void shouldExchangeAuthorizationCodeWhenAdditionalInformationUsesCamelPublicClient() {
        // isPublicClient 的驼峰 key 回退：publicClient（驼峰）也要认
        OAuth2ClientDO client = cimdClient()
                .setAdditionalInformation("{\"publicClient\":true}");
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);
        when(codeExtraRedisDAO.get("code-123")).thenReturn(codeExtra());
        when(oauth2CodeService.consumeAuthorizationCode("code-123")).thenReturn(codeDO());
        when(oauth2TokenService.createAccessToken(eq(1L), eq(2), eq(client), eq(List.of("mcp:access"))))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE);

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
    }

    @Test
    void shouldFailExchangeWhenAdditionalInformationIsInvalidJson() {
        // additionalInformation 非法 JSON：解析失败按“不是公开客户端”处理，抛 invalid_client，
        // 而不是把解析异常漏出去变成 500
        OAuth2ClientDO client = cimdClient().setAdditionalInformation("not-json{");
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
        verify(codeExtraRedisDAO, never()).get(anyString());
    }

    @Test
    void shouldRejectExchangeWhenClientIsNotPublicForCimdClient() {
        OAuth2ClientDO client = cimdClient()
                .setAdditionalInformation("{\"token_endpoint_auth_method\":\"client_secret_basic\"}");
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(client);

        assertThatThrownBy(() -> service.exchangeAuthorizationCode(
                CLIENT_ID_URL, "code-123", REDIRECT_URI, CODE_VERIFIER, RESOURCE, RESOURCE))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
        verify(oauth2CodeService, never()).consumeAuthorizationCode(anyString());
    }

    @Test
    void shouldRejectRefreshWhenRefreshTokenNotFound() {
        // 刷新先查令牌再解析客户端：令牌不存在直接抛 invalid_grant，不触发任何客户端解析 / 查库
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1")).thenReturn(null);

        assertThatThrownBy(() -> service.refreshAccessToken(CLIENT_ID_URL, "refresh-token-1"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verifyNoInteractions(clientResolver, oauth2ClientService);
    }

    @Test
    void shouldRejectRefreshWhenRefreshTokenClientMismatches() {
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId("mcp-dcr-other");
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);

        assertThatThrownBy(() -> service.refreshAccessToken(CLIENT_ID_URL, "refresh-token-1"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_grant");
        verifyNoInteractions(clientResolver, oauth2ClientService);
    }

    @Test
    void shouldRefreshAccessTokenForDbClientUnchanged() {
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache("mcp-dcr-abc", null,
                "refresh_token", null, null)).thenReturn(dbClient());
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId("mcp-dcr-abc");
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        when(oauth2TokenService.refreshAccessToken("refresh-token-1", "mcp-dcr-abc"))
                .thenReturn(accessTokenDO());

        OAuth2OpenAccessTokenRespVO respVO = service.refreshAccessToken("mcp-dcr-abc", "refresh-token-1");

        assertThat(respVO.getAccessToken()).isEqualTo("access-token-1");
        verify(clientResolver, never()).resolve(anyString(), any());
    }

    @Test
    void shouldRevokeTokenForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
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
        when(oauth2TokenService.getAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                        .setClientId("mcp-dcr-other"));

        assertThat(service.revokeToken(CLIENT_ID_URL, "access-token-1")).isFalse();
        verify(oauth2TokenService, never()).removeAccessToken(anyString());
    }

    @Test
    void shouldRevokeTokenForCimdClientWhenDocumentUnavailable() {
        // CIMD 文档不可用时（元数据 host 挂了），已有令牌仍能吊销：吊销只做形态判定，不拉取文档。
        // lenient 打桩表述“文档不可用”的前提，生产代码根本不应调 resolve()，故该打桩永不命中。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        lenient().when(clientResolver.resolve(CLIENT_ID_URL, null))
                .thenThrow(McpOAuthException.invalidClient("client metadata document is invalid (FETCH_FAILED)"));
        when(oauth2TokenService.getAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                        .setClientId(CLIENT_ID_URL));
        when(oauth2TokenService.removeAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1"));

        assertThat(service.revokeToken(CLIENT_ID_URL, "access-token-1")).isTrue();
        verify(clientResolver, never()).resolve(anyString(), any());
        verifyNoInteractions(oauth2ClientService);
    }

    @Test
    void shouldReturnFalseWhenRevokingUnknownToken() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(oauth2TokenService.getAccessToken("access-token-1")).thenReturn(null);

        assertThat(service.revokeToken(CLIENT_ID_URL, "access-token-1")).isFalse();
        verify(oauth2TokenService, never()).removeAccessToken(anyString());
    }

    @Test
    void shouldRevokeTokenForDbClient() {
        // DB 客户端的吊销成功路径：先走上游 validOAuthClientFromCache 校验客户端，再删令牌
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache("mcp-dcr-abc", null, null, null, null))
                .thenReturn(dbClient());
        when(oauth2TokenService.getAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                        .setClientId("mcp-dcr-abc"));
        when(oauth2TokenService.removeAccessToken("access-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("access-token-1"));

        assertThat(service.revokeToken("mcp-dcr-abc", "access-token-1")).isTrue();
        verify(oauth2ClientService).validOAuthClientFromCache("mcp-dcr-abc", null, null, null, null);
        verify(clientResolver, never()).resolve(anyString(), any());
        verify(oauth2TokenService).removeAccessToken("access-token-1");
    }

    @Test
    void shouldRevokeRefreshToken() {
        // refresh token 值：getAccessToken 的回退把它拼成 access token DO（N1），
        // clientId 能对上；但 removeAccessToken 按 access 表查不到，返回 null，
        // 此时必须走 refresh 分支：删 refresh 行 + 同 grant 全部 access token。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(oauth2TokenService.getAccessToken("refresh-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("refresh-token-1")
                        .setClientId(CLIENT_ID_URL));
        when(oauth2TokenService.removeAccessToken("refresh-token-1")).thenReturn(null);
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId(CLIENT_ID_URL);
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                .setClientId(CLIENT_ID_URL).setRefreshToken("refresh-token-1");
        accessTokenDO.setId(1L);
        when(oauth2AccessTokenMapper.selectListByRefreshToken("refresh-token-1"))
                .thenReturn(List.of(accessTokenDO));

        assertThat(service.revokeToken(CLIENT_ID_URL, "refresh-token-1")).isTrue();

        verify(oauth2AccessTokenMapper).deleteById(1L);
        verify(oauth2AccessTokenRedisDAO).deleteList(Set.of("access-token-1"));
        verify(oauth2RefreshTokenMapper).deleteByRefreshToken("refresh-token-1");
        verify(oauth2AccessTokenRedisDAO).delete("refresh-token-1");
    }

    @Test
    void shouldRevokeRefreshTokenWithoutAccessTokens() {
        // 同 grant 已无 access token 行：仍要删 refresh 行 + Redis 缓存键，
        // 否则 getAccessToken 回退缓存的已转换 access token 还能继续用。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(oauth2TokenService.getAccessToken("refresh-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("refresh-token-1")
                        .setClientId(CLIENT_ID_URL));
        when(oauth2TokenService.removeAccessToken("refresh-token-1")).thenReturn(null);
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId(CLIENT_ID_URL);
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        when(oauth2AccessTokenMapper.selectListByRefreshToken("refresh-token-1"))
                .thenReturn(List.of());

        assertThat(service.revokeToken(CLIENT_ID_URL, "refresh-token-1")).isTrue();

        verify(oauth2AccessTokenMapper, never()).deleteById(any());
        verify(oauth2AccessTokenRedisDAO, never()).deleteList(any());
        verify(oauth2RefreshTokenMapper).deleteByRefreshToken("refresh-token-1");
        verify(oauth2AccessTokenRedisDAO).delete("refresh-token-1");
    }

    @Test
    void shouldNotRevokeRefreshTokenForOtherClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(oauth2TokenService.getAccessToken("refresh-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("refresh-token-1")
                        .setClientId(CLIENT_ID_URL));
        when(oauth2TokenService.removeAccessToken("refresh-token-1")).thenReturn(null);
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId("mcp-dcr-other");
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);

        assertThat(service.revokeToken(CLIENT_ID_URL, "refresh-token-1")).isFalse();

        verify(oauth2AccessTokenMapper, never()).selectListByRefreshToken(anyString());
        verify(oauth2RefreshTokenMapper, never()).deleteByRefreshToken(anyString());
        verify(oauth2AccessTokenRedisDAO, never()).delete(anyString());
    }

    @Test
    void shouldReturnFalseWhenRevokingUnknownRefreshToken() {
        // access 表与 refresh 表都查不到：返回 false（控制器仍回 200，不透露令牌是否存在）。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(oauth2TokenService.getAccessToken("refresh-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("refresh-token-1")
                        .setClientId(CLIENT_ID_URL));
        when(oauth2TokenService.removeAccessToken("refresh-token-1")).thenReturn(null);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1")).thenReturn(null);

        assertThat(service.revokeToken(CLIENT_ID_URL, "refresh-token-1")).isFalse();

        verify(oauth2RefreshTokenMapper, never()).deleteByRefreshToken(anyString());
        verify(oauth2AccessTokenRedisDAO, never()).delete(anyString());
    }

    @Test
    void shouldRevokeExpiredRefreshToken() {
        // 过期 refresh：getAccessToken 返回 null（上游拒过期值），仍落到 refresh 分支，
        // 把残留行删掉；过期值本身已不可用，删行只是不留残留。
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(oauth2TokenService.getAccessToken("refresh-token-1")).thenReturn(null);
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId(CLIENT_ID_URL);
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        when(oauth2AccessTokenMapper.selectListByRefreshToken("refresh-token-1"))
                .thenReturn(List.of());

        assertThat(service.revokeToken(CLIENT_ID_URL, "refresh-token-1")).isTrue();

        verify(oauth2RefreshTokenMapper).deleteByRefreshToken("refresh-token-1");
        verify(oauth2AccessTokenRedisDAO).delete("refresh-token-1");
    }

    @Test
    void shouldRevokeRefreshTokenForDbClient() {
        // DB 客户端的 refresh 吊销：先走 validatePublicClient 校验客户端，再进 refresh 分支。
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache("mcp-dcr-abc", null, null, null, null))
                .thenReturn(dbClient());
        when(oauth2TokenService.getAccessToken("refresh-token-1"))
                .thenReturn(new OAuth2AccessTokenDO().setAccessToken("refresh-token-1")
                        .setClientId("mcp-dcr-abc"));
        when(oauth2TokenService.removeAccessToken("refresh-token-1")).thenReturn(null);
        OAuth2RefreshTokenDO refreshTokenDO = new OAuth2RefreshTokenDO().setRefreshToken("refresh-token-1")
                .setClientId("mcp-dcr-abc");
        refreshTokenDO.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("refresh-token-1"))
                .thenReturn(refreshTokenDO);
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO().setAccessToken("access-token-1")
                .setClientId("mcp-dcr-abc").setRefreshToken("refresh-token-1");
        accessTokenDO.setId(1L);
        when(oauth2AccessTokenMapper.selectListByRefreshToken("refresh-token-1"))
                .thenReturn(List.of(accessTokenDO));

        assertThat(service.revokeToken("mcp-dcr-abc", "refresh-token-1")).isTrue();

        verify(oauth2ClientService).validOAuthClientFromCache("mcp-dcr-abc", null, null, null, null);
        verify(oauth2AccessTokenMapper).deleteById(1L);
        verify(oauth2AccessTokenRedisDAO).deleteList(Set.of("access-token-1"));
        verify(oauth2RefreshTokenMapper).deleteByRefreshToken("refresh-token-1");
        verify(oauth2AccessTokenRedisDAO).delete("refresh-token-1");
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
