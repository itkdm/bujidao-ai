package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdError;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdMetadata;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthClientResolverTest {

    private static final String CLIENT_ID_URL = "https://client.example.com/app";
    private static final String REDIRECT_URI = "https://client.example.com/callback";

    @Mock
    private OAuth2ClientService oauth2ClientService;
    @Mock
    private ObjectProvider<McpCimdClientService> cimdClientServiceProvider;
    @Mock
    private McpCimdClientService cimdClientService;

    private McpOAuthProperties properties;
    private McpOAuthClientResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new McpOAuthProperties();
        resolver = new McpOAuthClientResolver(oauth2ClientService, cimdClientServiceProvider, properties);
    }

    private void givenCimdServiceAvailable() {
        when(cimdClientServiceProvider.getIfAvailable()).thenReturn(cimdClientService);
    }

    @Test
    void shouldReturnDbClientForNonUrlClientId() {
        OAuth2ClientDO dbClient = new OAuth2ClientDO().setClientId("mcp-dcr-abc").setSecret("secret");
        when(oauth2ClientService.getOAuth2ClientFromCache("mcp-dcr-abc")).thenReturn(dbClient);

        assertThat(resolver.isCimdClientId("mcp-dcr-abc")).isFalse();
        assertThat(resolver.resolve("mcp-dcr-abc", null)).isSameAs(dbClient);
        verifyNoInteractions(cimdClientService);
        verify(cimdClientServiceProvider, never()).getIfAvailable();
    }

    @Test
    void shouldReturnNullWhenDbClientNotFound() {
        when(oauth2ClientService.getOAuth2ClientFromCache("mcp-dcr-missing")).thenReturn(null);

        assertThat(resolver.resolve("mcp-dcr-missing", null)).isNull();
    }

    @Test
    void shouldSynthesizeClientFromMetadataWithRedirectUri() throws Exception {
        givenCimdServiceAvailable();
        when(cimdClientService.resolve(CLIENT_ID_URL, REDIRECT_URI)).thenReturn(metadataWithAllowedScope());

        OAuth2ClientDO client = resolver.resolve(CLIENT_ID_URL, REDIRECT_URI);

        verify(cimdClientService).resolve(CLIENT_ID_URL, REDIRECT_URI);
        verify(cimdClientService, never()).resolveMetadata(anyString());
        assertSynthesizedClient(client);
    }

    @Test
    void shouldSynthesizeClientFromMetadataWithoutRedirectUri() throws Exception {
        givenCimdServiceAvailable();
        when(cimdClientService.resolveMetadata(CLIENT_ID_URL)).thenReturn(metadataWithAllowedScope());

        OAuth2ClientDO client = resolver.resolve(CLIENT_ID_URL, null);

        verify(cimdClientService).resolveMetadata(CLIENT_ID_URL);
        verify(cimdClientService, never()).resolve(anyString(), anyString());
        assertSynthesizedClient(client);
    }

    @Test
    void shouldApplyDefaultsWhenMetadataOmitsOptionalFields() throws Exception {
        givenCimdServiceAvailable();
        McpCimdMetadata metadata = new McpCimdMetadata(CLIENT_ID_URL, "Minimal App",
                List.of(REDIRECT_URI), List.of("authorization_code"), List.of("code"),
                "none", null, null, null, null, null, "web");
        when(cimdClientService.resolveMetadata(CLIENT_ID_URL)).thenReturn(metadata);

        OAuth2ClientDO client = resolver.resolve(CLIENT_ID_URL, null);

        assertThat(client.getClientId()).isEqualTo(CLIENT_ID_URL);
        assertThat(client.getName()).isEqualTo("Minimal App");
        assertThat(client.getLogo()).isEqualTo(properties.getDefaultLogo());
        assertThat(client.getScopes()).containsExactly("mcp:access");
        assertThat(client.getAuthorizedGrantTypes()).containsExactly("authorization_code");
        assertThat(client.getAdditionalInformation()).contains("\"token_endpoint_auth_method\":\"none\"");
    }

    @Test
    void shouldDefaultEmptyGrantTypesToAuthorizationCodeOnly() throws Exception {
        givenCimdServiceAvailable();
        McpCimdMetadata metadata = new McpCimdMetadata(CLIENT_ID_URL, "Minimal App",
                List.of(REDIRECT_URI), List.of(), List.of("code"),
                "none", null, null, null, null, null, "web");
        when(cimdClientService.resolveMetadata(CLIENT_ID_URL)).thenReturn(metadata);

        OAuth2ClientDO client = resolver.resolve(CLIENT_ID_URL, null);

        // 防御性兜底只补 authorization_code，不悄悄加 refresh_token
        assertThat(client.getAuthorizedGrantTypes()).containsExactly("authorization_code");
    }

    @Test
    void shouldRejectScopeOutsideDefaultScopes() throws Exception {
        givenCimdServiceAvailable();
        when(cimdClientService.resolveMetadata(CLIENT_ID_URL)).thenReturn(metadata());

        assertThatThrownBy(() -> resolver.resolve(CLIENT_ID_URL, null))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_scope");
    }

    @Test
    void shouldAllowHttpClientIdWhenAllowPrivateAddresses() {
        properties.getCimd().setAllowPrivateAddresses(true);

        assertThat(resolver.isCimdClientId("http://127.0.0.1:8080/app")).isTrue();
        assertThat(resolver.isCimdClientId("https://client.example.com/app")).isTrue();
    }

    @Test
    void shouldThrowInvalidClientWhenFetchFails() throws Exception {
        givenCimdServiceAvailable();
        when(cimdClientService.resolveMetadata(CLIENT_ID_URL))
                .thenThrow(new McpCimdException(McpCimdError.FETCH_FAILED, "fetch failed"));

        assertThatThrownBy(() -> resolver.resolve(CLIENT_ID_URL, null))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
    }

    @Test
    void shouldThrowRateLimitedWhenCimdFetchIsRateLimited() throws Exception {
        givenCimdServiceAvailable();
        when(cimdClientService.resolveMetadata(CLIENT_ID_URL))
                .thenThrow(new McpCimdException(McpCimdError.RATE_LIMITED, "CIMD fetch rate limited"));

        // 本测试只锁定：拉取层限流转 429 + temporarily_unavailable，描述里保留 RATE_LIMITED 类别
        assertThatThrownBy(() -> resolver.resolve(CLIENT_ID_URL, null))
                .isInstanceOf(McpOAuthException.class)
                .satisfies(exception -> {
                    McpOAuthException oauthException = (McpOAuthException) exception;
                    assertThat(oauthException.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(oauthException.getError()).isEqualTo("temporarily_unavailable");
                    assertThat(oauthException.getRetryAfterSeconds()).isEqualTo(60L);
                    assertThat(oauthException.getDescription()).contains("RATE_LIMITED");
                });
    }

    @Test
    void shouldThrowInvalidClientWhenRedirectUriNotRegistered() throws Exception {
        givenCimdServiceAvailable();
        when(cimdClientService.resolve(CLIENT_ID_URL, "https://client.example.com/other"))
                .thenThrow(new McpCimdException(McpCimdError.INVALID_REDIRECT_URI,
                        "redirect_uri is not registered in the client metadata document"));

        assertThatThrownBy(() -> resolver.resolve(CLIENT_ID_URL, "https://client.example.com/other"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
    }

    @Test
    void shouldThrowInvalidClientWhenCimdServiceIsUnavailable() {
        when(cimdClientServiceProvider.getIfAvailable()).thenReturn(null);
        McpOAuthClientResolver disabledResolver =
                new McpOAuthClientResolver(oauth2ClientService, cimdClientServiceProvider, properties);

        assertThatThrownBy(() -> disabledResolver.resolve(CLIENT_ID_URL, null))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_client");
    }

    @Test
    void shouldDetectCimdClientIdShape() {
        assertThat(resolver.isCimdClientId(CLIENT_ID_URL)).isTrue();
        assertThat(resolver.isCimdClientId("https://client.example.com/app?tenant=1")).isTrue();
        assertThat(resolver.isCimdClientId("mcp-dcr-abc")).isFalse();
        assertThat(resolver.isCimdClientId("not-a-url")).isFalse();
        assertThat(resolver.isCimdClientId("https://example.com")).isFalse();
        assertThat(resolver.isCimdClientId("https://example.com/")).isFalse();
        assertThat(resolver.isCimdClientId("http://127.0.0.1:8080/app")).isFalse();
        assertThat(resolver.isCimdClientId(null)).isFalse();
        assertThat(resolver.isCimdClientId("  ")).isFalse();
    }

    private void assertSynthesizedClient(OAuth2ClientDO client) {
        assertThat(client.getClientId()).isEqualTo(CLIENT_ID_URL);
        assertThat(client.getSecret()).isEmpty();
        assertThat(client.getName()).isEqualTo("Test App");
        assertThat(client.getLogo()).isEqualTo("https://client.example.com/logo.png");
        assertThat(client.getRedirectUris()).containsExactly(REDIRECT_URI);
        assertThat(client.getAuthorizedGrantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(client.getScopes()).containsExactly("mcp:access");
        assertThat(client.getStatus()).isEqualTo(0);
        assertThat(client.getAccessTokenValiditySeconds())
                .isEqualTo(properties.getAccessTokenValiditySeconds());
        assertThat(client.getRefreshTokenValiditySeconds())
                .isEqualTo(properties.getRefreshTokenValiditySeconds());
        assertThat(client.getAdditionalInformation())
                .contains("\"token_endpoint_auth_method\":\"none\"")
                .contains("\"public_client\":true")
                .contains("\"require_pkce\":true")
                .contains("\"cimd_client\":true")
                .doesNotContain("mcp_resource");
        assertThat(client.getId()).isNull();
    }

    private static McpCimdMetadata metadata() {
        return new McpCimdMetadata(CLIENT_ID_URL, "Test App",
                List.of(REDIRECT_URI), List.of("authorization_code", "refresh_token"), List.of("code"),
                "none", null, null, "https://client.example.com",
                "https://client.example.com/logo.png", "mcp:access mcp:other", "web");
    }

    private static McpCimdMetadata metadataWithAllowedScope() {
        return new McpCimdMetadata(CLIENT_ID_URL, "Test App",
                List.of(REDIRECT_URI), List.of("authorization_code", "refresh_token"), List.of("code"),
                "none", null, null, "https://client.example.com",
                "https://client.example.com/logo.png", "mcp:access", "web");
    }

}
