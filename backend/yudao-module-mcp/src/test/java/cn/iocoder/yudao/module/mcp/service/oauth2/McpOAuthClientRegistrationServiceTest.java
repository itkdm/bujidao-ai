package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationReqVO;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationRespVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2ClientMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthClientRegistrationServiceTest {

    @Mock
    private OAuth2ClientMapper oauth2ClientMapper;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    private McpOAuthProperties properties;
    private McpOAuthClientRegistrationService service;

    @BeforeEach
    void setUp() {
        properties = new McpOAuthProperties();
        service = new McpOAuthClientRegistrationService(properties, oauth2ClientMapper, cacheManager);
    }

    @Test
    void shouldCreatePublicPkceClientWithDefaultScope() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setClientName("Codex");
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback/test"));
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        OAuth2ClientDO client = clientCaptor.getValue();
        // 只逐出本次注册的客户端：全量 clear 会把其他客户端的缓存条目也清掉。
        verify(cache).evict(client.getClientId());
        verify(cache, never()).clear();
        assertThat(response.getClientId()).isEqualTo(client.getClientId()).startsWith("mcp-dcr-");
        assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(response.getScope()).isEqualTo("mcp:access");
        assertThat(client.getSecret()).isEmpty();
        assertThat(client.getScopes()).containsExactly("mcp:access");
        assertThat(client.getAutoApproveScopes()).isEmpty();
        // 未声明 grant_types 时缺省含 refresh_token（与 CIMD 校验器缺省一致），
        // 否则该客户端 30 分钟后刷新会被 validateCimdGrant 拒绝。
        assertThat(client.getAuthorizedGrantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(response.getGrantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(client.getAdditionalInformation())
                .contains("\"token_endpoint_auth_method\":\"none\"")
                .contains("\"public_client\":true")
                .contains("\"require_pkce\":true")
                .contains("\"mcp_resource\":\"http://127.0.0.1:48080/mcp\"");
    }

    @Test
    void shouldRejectUnsupportedRedirectUri() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("javascript:alert(1)"));

        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_redirect_uri");
    }

    @Test
    void shouldRejectDangerousPrivateSchemeRedirectUris() {
        // 覆盖黑名单全部 34 个 scheme（http/https 走回环分支，同样拒绝非回环 host），
        // 逐个断言：黑名单里某一项拼错，测试会立即发现。另加一个大写变体，确认
        // scheme 比对不区分大小写。
        List<String> dangerousUris = List.of(
                "http://attacker.example/cb",
                "https://attacker.example/cb",
                "javascript:alert(1)",
                "data:text/html,hi",
                "file:///etc/passwd",
                "ftp://attacker.example/x",
                "mailto:a@b.example",
                "tel:+123",
                "sms:+123",
                "vbscript:alert(1)",
                "intent:example",
                "ms-msdt:/id",
                "search-ms:query=test",
                "ms-officeapp:edit",
                "ms-excel:edit",
                "ms-word:edit",
                "ms-powerpoint:edit",
                "ms-access:edit",
                "ms-publisher:edit",
                "ms-visio:edit",
                "ms-project:edit",
                "ms-settings:network",
                "ms-screenclip:edit",
                "ms-gamingoverlay:edit",
                "ms-browser-extension:edit",
                "ms-calculator:edit",
                "calculator:edit",
                "ms-people:edit",
                "ms-call:edit",
                "ms-sms:edit",
                "ms-chat:edit",
                "ms-store:edit",
                "ms-windows-store://pdp/?ProductId=1",
                "microsoft-edge:https://attacker.example/cb",
                // scheme 比对不区分大小写，同样拒绝
                "MS-MSDT:/id");
        for (String redirectUri : dangerousUris) {
            McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
            reqVO.setRedirectUris(List.of(redirectUri));
            assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                    .as("redirectUri=%s", redirectUri)
                    .isInstanceOf(McpOAuthException.class)
                    .extracting("status", "error")
                    .containsExactly(HttpStatus.BAD_REQUEST, "invalid_redirect_uri");
        }
    }

    @Test
    void shouldAcceptRealWorkBuddyDeepLinkScheme() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setClientName("WorkBuddy");
        reqVO.setRedirectUris(List.of("workbuddy-ai://workbuddy/mcp/callback"));
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        assertThat(response.getClientId()).startsWith("mcp-dcr-");
        assertThat(response.getRedirectUris()).containsExactly("workbuddy-ai://workbuddy/mcp/callback");
        verify(cache, never()).clear();
    }

    @Test
    void shouldRejectRegistrationWhenDcrIsDisabled() {
        properties.setDynamicClientRegistrationEnabled(false);

        assertThatThrownBy(() -> service.register(new McpOAuthClientRegistrationReqVO(),
                "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.NOT_FOUND, "invalid_request");
    }

    @Test
    void shouldAcceptAndStripOfflineAccessScope() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setScope("mcp:access offline_access");
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        // offline_access 只表达刷新意图：接受、不拒绝，但剥离、不计入授予 scope；
        // 响应 scope 由剥离后的授予 scope 生成，不含 offline_access（与 CIMD 的 parseScopes 一致）。
        assertThat(response.getScope()).isEqualTo("mcp:access");
        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getScopes()).containsExactly("mcp:access");
        verify(cache).evict(clientCaptor.getValue().getClientId());
        verify(cache, never()).clear();
    }

    @Test
    void shouldDefaultScopesWhenOnlyOfflineAccessRequested() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setScope("offline_access");
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        // offline_access 剥离后为空，视同未声明 scope，取 defaultScopes。
        assertThat(response.getScope()).isEqualTo("mcp:access");
        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getScopes()).containsExactly("mcp:access");
        verify(cache).evict(clientCaptor.getValue().getClientId());
        verify(cache, never()).clear();
    }

    @Test
    void shouldAcceptScopeWithExtraSpacesAndDuplicates() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setScope("  mcp:access   offline_access  mcp:access ");
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        // 多余空格与重复 scope 先归一化（去空白、去重），再剥离 offline_access。
        assertThat(response.getScope()).isEqualTo("mcp:access");
        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getScopes()).containsExactly("mcp:access");
    }

    @Test
    void shouldRejectOfflineAccessWithWrongCase() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setScope("mcp:access Offline_Access");

        // scope 区分大小写（RFC 6749 §3.3）：剥离只认小写 offline_access，
        // 大写变体不剥离，仍按超限 scope 拒 invalid_scope（与 CIMD 一致）。
        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_scope");
    }

    @Test
    void shouldRejectOtherScopeEvenWithOfflineAccess() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setScope("mcp:other offline_access");

        // 剥离 offline_access 后仍有超限 scope，照常拒 invalid_scope。
        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_scope");
    }

    @Test
    void shouldRejectScopeOverLimit() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setScope("mcp:other");

        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_scope");
    }

    @Test
    void shouldAcceptPrivateSchemeRedirectWhenAllowed() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setClientName("WorkBuddy");
        reqVO.setRedirectUris(List.of("workbuddy://oauth/callback"));
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        assertThat(response.getClientId()).startsWith("mcp-dcr-");
        assertThat(response.getRedirectUris()).containsExactly("workbuddy://oauth/callback");
        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getRedirectUris()).containsExactly("workbuddy://oauth/callback");
        verify(cache, never()).clear();
    }

    @Test
    void shouldRejectPrivateSchemeRedirectWhenDisabled() {
        properties.setAllowPrivateUseUriSchemeRedirects(false);
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("workbuddy://oauth/callback"));

        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_redirect_uri");
    }

    @Test
    void shouldRejectGrantTypesWithoutAuthorizationCode() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setGrantTypes(List.of("refresh_token"));

        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void shouldRejectNonNoneAuthMethod() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setTokenEndpointAuthMethod("client_secret_basic");

        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void shouldCreateClientWithRefreshGrant() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback"));
        reqVO.setGrantTypes(List.of("authorization_code", "refresh_token"));
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        assertThat(response.getGrantTypes()).containsExactly("authorization_code", "refresh_token");
        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getAuthorizedGrantTypes())
                .containsExactly("authorization_code", "refresh_token");
        verify(cache, never()).clear();
    }

}
