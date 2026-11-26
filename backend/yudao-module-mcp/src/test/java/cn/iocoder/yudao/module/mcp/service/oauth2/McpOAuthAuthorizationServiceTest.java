package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationRequestRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdMetadata;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ApproveDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ApproveService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2CodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthAuthorizationServiceTest {

    private static final String CLIENT_ID_URL = "https://client.example.com/app";
    private static final String REDIRECT_URI = "https://client.example.com/callback";
    private static final String RESOURCE = "https://mcp.example.com/mcp";
    private static final String CODE_VERIFIER = "test-code-verifier-0123456789abcdef";

    @Mock
    private OAuth2ClientService oauth2ClientService;
    @Mock
    private OAuth2ApproveService oauth2ApproveService;
    @Mock
    private OAuth2CodeService oauth2CodeService;
    @Mock
    private McpOAuthAuthorizationCodeExtraRedisDAO codeExtraRedisDAO;
    @Mock
    private McpOAuthAuthorizationRequestRedisDAO authorizationRequestRedisDAO;
    @Mock
    private McpOAuthClientResolver clientResolver;

    private McpOAuthAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new McpOAuthAuthorizationService(oauth2ClientService, oauth2ApproveService,
                oauth2CodeService, codeExtraRedisDAO, authorizationRequestRedisDAO, clientResolver);
    }

    @Test
    void shouldGetAuthorizeInfoForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null)).thenReturn(cimdClient());
        when(oauth2ApproveService.getApproveList(isNull(), eq(2), eq(CLIENT_ID_URL))).thenReturn(List.of());

        OAuth2OpenAuthorizeInfoRespVO respVO = service.getAuthorizeInfo(CLIENT_ID_URL);

        assertThat(respVO.getClient().getName()).isEqualTo("Test App");
        assertThat(respVO.getClient().getLogo()).isEqualTo("https://client.example.com/logo.png");
        assertThat(respVO.getScopes()).hasSize(1);
        assertThat(respVO.getScopes().get(0).getKey()).isEqualTo("mcp:access");
        assertThat(respVO.getScopes().get(0).getValue()).isFalse();
        verifyNoInteractions(oauth2ClientService);
    }

    @Test
    void shouldFailAuthorizeInfoWhenCimdDocumentIsInvalid() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, null))
                .thenThrow(McpOAuthException.invalidClient("client metadata document is invalid (FETCH_FAILED)"));

        assertThatThrownBy(() -> service.getAuthorizeInfo(CLIENT_ID_URL))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("invalid_client")
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(400);
    }

    @Test
    void shouldGetAuthorizeInfoForDbClientUnchanged() {
        OAuth2ClientDO dbClient = new OAuth2ClientDO().setClientId("mcp-dcr-abc").setName("Db App")
                .setLogo("https://example.com/logo.png").setScopes(List.of("mcp:access"));
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache("mcp-dcr-abc")).thenReturn(dbClient);
        when(oauth2ApproveService.getApproveList(isNull(), eq(2), eq("mcp-dcr-abc"))).thenReturn(List.of());

        OAuth2OpenAuthorizeInfoRespVO respVO = service.getAuthorizeInfo("mcp-dcr-abc");

        assertThat(respVO.getClient().getName()).isEqualTo("Db App");
        verify(clientResolver, never()).resolve(anyString(), any());
    }

    @Test
    void shouldApproveCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(cimdClient());
        when(oauth2ApproveService.updateAfterApproval(isNull(), eq(2), eq(CLIENT_ID_URL), any()))
                .thenReturn(true);
        when(authorizationRequestRedisDAO.markUsed(anyString())).thenReturn(true);
        when(oauth2CodeService.createAuthorizationCode(isNull(), eq(2), eq(CLIENT_ID_URL),
                eq(List.of("mcp:access")), eq(REDIRECT_URI), eq("state-1")))
                .thenReturn(new OAuth2CodeDO().setCode("code-123").setUserId(null).setUserType(2)
                        .setClientId(CLIENT_ID_URL).setRedirectUri(REDIRECT_URI)
                        .setExpiresTime(LocalDateTime.now().plusSeconds(300)));

        String redirect = service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE);

        assertThat(redirect).startsWith(REDIRECT_URI).contains("code=code-123").contains("state=state-1");
        verify(codeExtraRedisDAO).set(any());
        verify(oauth2ApproveService, never()).checkForPreApproval(any(), any(), anyString(), anyCollection());
    }

    @Test
    void shouldApproveCimdClientWithRealResolver() throws Exception {
        // 集成用例：真实 Resolver（只 mock 底下的 McpCimdClientService，不 mock Resolver 本身），
        // 跑通授权发码，覆盖元数据 → 合成客户端的字段映射链
        McpCimdClientService cimdClientService = org.mockito.Mockito.mock(McpCimdClientService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<McpCimdClientService> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cimdClientService);
        when(cimdClientService.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(new McpCimdMetadata(
                CLIENT_ID_URL, "Test App", List.of(REDIRECT_URI),
                List.of("authorization_code", "refresh_token"), List.of("code"),
                "none", null, null, "https://client.example.com",
                "https://client.example.com/logo.png", "mcp:access", "web"));
        McpOAuthClientResolver realResolver =
                new McpOAuthClientResolver(oauth2ClientService, provider, new McpOAuthProperties());
        McpOAuthAuthorizationService realService = new McpOAuthAuthorizationService(oauth2ClientService,
                oauth2ApproveService, oauth2CodeService, codeExtraRedisDAO, authorizationRequestRedisDAO,
                realResolver);
        when(oauth2ApproveService.updateAfterApproval(isNull(), eq(2), eq(CLIENT_ID_URL), any()))
                .thenReturn(true);
        when(authorizationRequestRedisDAO.markUsed(anyString())).thenReturn(true);
        when(oauth2CodeService.createAuthorizationCode(isNull(), eq(2), eq(CLIENT_ID_URL),
                eq(List.of("mcp:access")), eq(REDIRECT_URI), eq("state-1")))
                .thenReturn(new OAuth2CodeDO().setCode("code-123").setUserId(null).setUserType(2)
                        .setClientId(CLIENT_ID_URL).setRedirectUri(REDIRECT_URI)
                        .setExpiresTime(LocalDateTime.now().plusSeconds(300)));

        String redirect = realService.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE);

        assertThat(redirect).startsWith(REDIRECT_URI).contains("code=code-123").contains("state=state-1");
        verify(cimdClientService).resolve(CLIENT_ID_URL, REDIRECT_URI);
    }

    @Test
    void shouldRejectCimdClientWithoutAuthorizationCodeGrant() {
        OAuth2ClientDO client = cimdClient().setAuthorizedGrantTypes(List.of("refresh_token"));
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(client);

        assertThatThrownBy(() -> service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE))
                .isInstanceOf(ServiceException.class)
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(1_002_020_003);
        verify(oauth2CodeService, never()).createAuthorizationCode(any(), any(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void shouldFailWithBadRequestWhenPkceFailsForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(cimdClient());

        assertThatThrownBy(() -> service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", null, "S256", RESOURCE, RESOURCE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("invalid_request")
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(400);
        verify(oauth2CodeService, never()).createAuthorizationCode(any(), any(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void shouldFailWithBadRequestWhenPkceFailsForDbClient() {
        OAuth2ClientDO dbClient = cimdClient().setClientId("mcp-dcr-abc");
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache(eq("mcp-dcr-abc"), isNull(),
                eq("authorization_code"), any(), isNull())).thenReturn(dbClient);

        assertThatThrownBy(() -> service.approveOrDeny("code", "mcp-dcr-abc", "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", null, "S256", RESOURCE, RESOURCE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("invalid_request")
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(400);
        verify(oauth2CodeService, never()).createAuthorizationCode(any(), any(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void shouldFailWithBadRequestWhenResourceMismatchesForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(cimdClient());

        assertThatThrownBy(() -> service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256",
                "https://other.example.com/mcp", RESOURCE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("resource")
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(400);
        verify(oauth2CodeService, never()).createAuthorizationCode(any(), any(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void shouldApproveDbClientUnchanged() {
        OAuth2ClientDO dbClient = cimdClient().setClientId("mcp-dcr-abc");
        when(clientResolver.isCimdClientId("mcp-dcr-abc")).thenReturn(false);
        when(oauth2ClientService.validOAuthClientFromCache(eq("mcp-dcr-abc"), isNull(),
                eq("authorization_code"), any(), isNull())).thenReturn(dbClient);
        when(oauth2ApproveService.updateAfterApproval(isNull(), eq(2), eq("mcp-dcr-abc"), any()))
                .thenReturn(true);
        when(authorizationRequestRedisDAO.markUsed(anyString())).thenReturn(true);
        when(oauth2CodeService.createAuthorizationCode(isNull(), eq(2), eq("mcp-dcr-abc"),
                eq(List.of("mcp:access")), eq(REDIRECT_URI), eq("state-1")))
                .thenReturn(new OAuth2CodeDO().setCode("code-123").setUserId(null).setUserType(2)
                        .setClientId("mcp-dcr-abc").setRedirectUri(REDIRECT_URI)
                        .setExpiresTime(LocalDateTime.now().plusSeconds(300)));

        String redirect = service.approveOrDeny("code", "mcp-dcr-abc", "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE);

        assertThat(redirect).startsWith(REDIRECT_URI).contains("code=code-123");
        verify(clientResolver, never()).resolve(anyString(), any());
    }

    @Test
    void shouldRejectUnregisteredRedirectUriForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI))
                .thenThrow(McpOAuthException.invalidClient(
                        "client metadata document is invalid (INVALID_REDIRECT_URI)"));

        assertThatThrownBy(() -> service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("invalid_client")
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(400);
        verify(oauth2CodeService, never()).createAuthorizationCode(any(), any(), anyString(),
                any(), anyString(), anyString());
    }

    @Test
    void shouldRejectOverScopeForCimdClient() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(cimdClient());

        assertThatThrownBy(() -> service.approveOrDeny("code", CLIENT_ID_URL, "{\"admin:all\":true}",
                REDIRECT_URI, false, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE))
                .isInstanceOf(ServiceException.class)
                .extracting(exception -> ((ServiceException) exception).getCode())
                .isEqualTo(1_002_020_004);
    }

    @Test
    void shouldAutoApproveCimdClientWithStoredApproval() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(cimdClient());
        when(oauth2ApproveService.getApproveList(isNull(), eq(2), eq(CLIENT_ID_URL)))
                .thenReturn(List.of(new OAuth2ApproveDO().setScope("mcp:access").setApproved(true)
                        .setExpiresTime(LocalDateTime.now().plusDays(1))));
        when(authorizationRequestRedisDAO.markUsed(anyString())).thenReturn(true);
        when(oauth2CodeService.createAuthorizationCode(isNull(), eq(2), eq(CLIENT_ID_URL),
                eq(List.of("mcp:access")), eq(REDIRECT_URI), eq("state-1")))
                .thenReturn(new OAuth2CodeDO().setCode("code-123").setUserId(null).setUserType(2)
                        .setClientId(CLIENT_ID_URL).setRedirectUri(REDIRECT_URI)
                        .setExpiresTime(LocalDateTime.now().plusSeconds(300)));

        String redirect = service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, true, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE);

        assertThat(redirect).contains("code=code-123");
        verify(oauth2ApproveService, never()).checkForPreApproval(any(), any(), anyString(), anyCollection());
    }

    @Test
    void shouldNotAutoApproveCimdClientWithoutStoredApproval() {
        when(clientResolver.isCimdClientId(CLIENT_ID_URL)).thenReturn(true);
        when(clientResolver.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(cimdClient());
        when(oauth2ApproveService.getApproveList(isNull(), eq(2), eq(CLIENT_ID_URL))).thenReturn(List.of());

        String redirect = service.approveOrDeny("code", CLIENT_ID_URL, "{\"mcp:access\":true}",
                REDIRECT_URI, true, "state-1", s256Challenge(CODE_VERIFIER), "S256", RESOURCE, RESOURCE);

        assertThat(redirect).isNull();
        verify(oauth2CodeService, never()).createAuthorizationCode(any(), any(), anyString(),
                any(), anyString(), anyString());
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
