package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCimdDocumentValidatorTest {

    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1");

    private static Map<String, Object> validDocument() {
        Map<String, Object> document = new HashMap<>();
        document.put("client_id", "https://client.example.com/metadata");
        document.put("client_name", "Test Client");
        document.put("redirect_uris", List.of("https://client.example.com/callback"));
        return document;
    }

    // ========== client_id URL 形状 ==========

    @Test
    void shouldAcceptValidClientIdUrl() throws McpCimdException {
        assertThat(McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/metadata"))
                .hasToString("https://client.example.com/metadata");
    }

    @Test
    void shouldAcceptClientIdUrlWithQuery() throws McpCimdException {
        // 草案对 query 只是 SHOULD NOT，不拦截
        assertThat(McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/metadata?tenant=a"))
                .isNotNull();
    }

    @Test
    void shouldRejectHttpClientIdUrlByDefault() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("http://client.example.com/metadata"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldAcceptHttpClientIdUrlWhenAllowHttp() throws McpCimdException {
        assertThat(McpCimdDocumentValidator.validateClientIdUrl("http://127.0.0.1:8080/metadata", true))
                .isNotNull();
    }

    @Test
    void shouldRejectClientIdUrlWithoutPath() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldRejectClientIdUrlWithRootPath() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldRejectClientIdUrlWithFragment() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/m#frag"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldRejectClientIdUrlWithUserinfo() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://user@client.example.com/m"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldRejectClientIdUrlWithDotSegments() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/a/../m"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/a/./m"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldRejectClientIdUrlWithEncodedDotSegments() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("https://client.example.com/a/%2e%2e/m"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldRejectBlankClientIdUrl() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateClientIdUrl("  "))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    // ========== 文档校验 ==========

    @Test
    void shouldValidateDocumentWithDefaults() throws McpCimdException {
        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", validDocument(), LOCAL_HOSTS);

        assertThat(metadata.clientId()).isEqualTo("https://client.example.com/metadata");
        assertThat(metadata.clientName()).isEqualTo("Test Client");
        assertThat(metadata.redirectUris()).containsExactly("https://client.example.com/callback");
        assertThat(metadata.grantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(metadata.responseTypes()).containsExactly("code");
        assertThat(metadata.tokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(metadata.applicationType()).isEqualTo("web");
    }

    @Test
    void shouldRejectDocumentWhenClientIdMismatches() {
        Map<String, Object> document = validDocument();
        document.put("client_id", "https://client.example.com/other");

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldDefaultClientNameToClientIdHostWhenMissing() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.remove("client_name");

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        // client_name 在 RFC 7591 / OIDC 里是 OPTIONAL：缺失时用 client_id URL 的 host 兜底。
        assertThat(metadata.clientName()).isEqualTo("client.example.com");
    }

    @Test
    void shouldDefaultClientNameToClientIdHostWhenBlank() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.put("client_name", "   ");

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        assertThat(metadata.clientName()).isEqualTo("client.example.com");
    }

    @Test
    void shouldRejectDocumentWithNonStringClientName() {
        Map<String, Object> document = validDocument();
        document.put("client_name", 123);

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectDocumentWithEmptyRedirectUris() {
        Map<String, Object> document = validDocument();
        document.put("redirect_uris", List.of());

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectDocumentWithNonStringRedirectUri() {
        Map<String, Object> document = validDocument();
        document.put("redirect_uris", List.of(123));

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectSharedSecretAuthMethod() {
        for (String method : List.of("client_secret_basic", "client_secret_post", "client_secret_jwt")) {
            Map<String, Object> document = validDocument();
            document.put("token_endpoint_auth_method", method);

            assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                    "https://client.example.com/metadata", document, LOCAL_HOSTS))
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.INVALID_DOCUMENT);
        }
    }

    @Test
    void shouldRejectPrivateKeyJwt() {
        // private_key_jwt 现阶段不支持：即使带了 jwks 也拒绝，待后续实现
        // client_assertion 验签后再放开（见 McpCimdDocumentValidator）
        Map<String, Object> withJwksUri = validDocument();
        withJwksUri.put("token_endpoint_auth_method", "private_key_jwt");
        withJwksUri.put("jwks_uri", "https://93.184.216.34/jwks.json");
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", withJwksUri, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);

        Map<String, Object> withJwks = validDocument();
        withJwks.put("token_endpoint_auth_method", "private_key_jwt");
        withJwks.put("jwks", Map.of("keys", List.of()));
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", withJwks, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectPrivateKeyJwtWithJwksUri() {
        Map<String, Object> document = validDocument();
        document.put("token_endpoint_auth_method", "private_key_jwt");
        // jwks_uri host 要做 DNS 解析判：用公开字面 IP，避免测试依赖外部 DNS
        //（93.184.216.34 是 example.com 的公开 IP）
        document.put("jwks_uri", "https://93.184.216.34/jwks.json");

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectHttpJwksUri() {
        Map<String, Object> document = validDocument();
        document.put("jwks_uri", "http://93.184.216.34/jwks.json");

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectPrivateIpJwksUri() {
        // 非公开地址由 SSRF guard 抛 SSRF_BLOCKED（只有 DNS 解析失败时才传
        // INVALID_DOCUMENT），与拉取层保持同一错误区分，不把两类失败混成一个码
        for (String jwksUri : List.of(
                "https://10.0.0.1/jwks.json",
                "https://127.0.0.1/jwks.json",
                "https://192.168.1.1/jwks.json",
                "https://localhost/jwks.json",
                "https://[::ffff:10.0.0.1]/jwks.json")) {
            Map<String, Object> document = validDocument();
            document.put("jwks_uri", jwksUri);

            assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                    "https://client.example.com/metadata", document, LOCAL_HOSTS))
                    .as("jwks_uri " + jwksUri + " should be rejected")
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.SSRF_BLOCKED);
        }
    }

    @Test
    void shouldRejectJwksUriWithFragmentOrUserinfo() {
        for (String jwksUri : List.of(
                "https://93.184.216.34/jwks.json#section",
                "https://user@93.184.216.34/jwks.json")) {
            Map<String, Object> document = validDocument();
            document.put("jwks_uri", jwksUri);

            assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                    "https://client.example.com/metadata", document, LOCAL_HOSTS))
                    .as("jwks_uri " + jwksUri + " should be rejected")
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.INVALID_DOCUMENT);
        }
    }

    @Test
    void shouldRejectPrivateKeyJwtWithJwksObject() {
        Map<String, Object> document = validDocument();
        document.put("token_endpoint_auth_method", "private_key_jwt");
        document.put("jwks", Map.of("keys", List.of()));

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectGrantTypesWithoutAuthorizationCode() {
        Map<String, Object> document = validDocument();
        document.put("grant_types", List.of("refresh_token"));

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectGrantTypesOutsideAllowedSet() {
        Map<String, Object> document = validDocument();
        document.put("grant_types", List.of("authorization_code", "implicit"));

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldAcceptGrantTypesSubset() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.put("grant_types", List.of("authorization_code", "refresh_token"));

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        assertThat(metadata.grantTypes()).containsExactly("authorization_code", "refresh_token");
    }

    @Test
    void shouldRejectResponseTypesOtherThanCode() {
        Map<String, Object> document = validDocument();
        document.put("response_types", List.of("code", "token"));

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldAcceptNativeApplicationType() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.put("application_type", "native");

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        assertThat(metadata.applicationType()).isEqualTo("native");
    }

    @Test
    void shouldRejectUnknownApplicationType() {
        for (String type : List.of("service", "webapp", "WEB", "desktop")) {
            Map<String, Object> document = validDocument();
            document.put("application_type", type);

            assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                    "https://client.example.com/metadata", document, LOCAL_HOSTS))
                    .as("application_type " + type + " should be rejected")
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.INVALID_DOCUMENT);
        }
    }

    @Test
    void shouldAcceptHttpsClientUriAndLogoUri() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.put("client_uri", "https://client.example.com/");
        document.put("logo_uri", "https://client.example.com/logo.png");

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        assertThat(metadata.clientUri()).isEqualTo("https://client.example.com/");
        assertThat(metadata.logoUri()).isEqualTo("https://client.example.com/logo.png");
    }

    @Test
    void shouldRejectNonHttpsClientUriAndLogoUri() {
        for (Map.Entry<String, String> entry : List.of(
                Map.entry("client_uri", "http://client.example.com/"),
                Map.entry("client_uri", "/relative"),
                Map.entry("client_uri", "https://client.example.com/#frag"),
                Map.entry("client_uri", "https://user@client.example.com/"),
                Map.entry("logo_uri", "http://client.example.com/logo.png"),
                Map.entry("logo_uri", "client.example.com/logo.png"))) {
            Map<String, Object> document = validDocument();
            document.put(entry.getKey(), entry.getValue());

            assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                    "https://client.example.com/metadata", document, LOCAL_HOSTS))
                    .as(entry.getKey() + " " + entry.getValue() + " should be rejected")
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.INVALID_DOCUMENT);
        }
    }

    // ========== redirect_uri 规则 ==========

    @Test
    void shouldAcceptHttpsRedirectUri() throws McpCimdException {
        McpCimdDocumentValidator.validateRedirectUri("https://client.example.com/callback", LOCAL_HOSTS);
    }

    @Test
    void shouldAcceptHttpLoopbackRedirectUri() throws McpCimdException {
        McpCimdDocumentValidator.validateRedirectUri("http://127.0.0.1:8080/callback", LOCAL_HOSTS);
        McpCimdDocumentValidator.validateRedirectUri("http://localhost:8080/callback", LOCAL_HOSTS);
        McpCimdDocumentValidator.validateRedirectUri("http://[::1]:8080/callback", LOCAL_HOSTS);
    }

    @Test
    void shouldAcceptHttpRedirectUriForLoopbackRangeAndTrailingDot() throws McpCimdException {
        // 127.0.0.0/8 整个段都是回环
        McpCimdDocumentValidator.validateRedirectUri("http://127.0.0.2:8080/callback", LOCAL_HOSTS);
        McpCimdDocumentValidator.validateRedirectUri("http://127.255.255.255:8080/callback", LOCAL_HOSTS);
        // DNS 尾点等价
        McpCimdDocumentValidator.validateRedirectUri("http://localhost.:8080/callback", LOCAL_HOSTS);
        McpCimdDocumentValidator.validateRedirectUri("http://127.0.0.1.:8080/callback", LOCAL_HOSTS);
        // 整型写法也认
        assertThat(McpCimdDocumentValidator.isLoopbackHost("2130706433", LOCAL_HOSTS)).isTrue();
        assertThat(McpCimdDocumentValidator.isLoopbackHost("0x7f000001", LOCAL_HOSTS)).isTrue();
        assertThat(McpCimdDocumentValidator.isLoopbackHost("127.0.0.2", LOCAL_HOSTS)).isTrue();
    }

    @Test
    void shouldRejectHttpsRedirectUriWithoutHostOrWithUserinfo() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri(
                "https:///callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri(
                "https://user@client.example.com/callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri(
                "http://user@127.0.0.1:8080/callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
    }

    @Test
    void shouldAcceptHttpRedirectUriForConfiguredLocalHost() throws McpCimdException {
        McpCimdDocumentValidator.validateRedirectUri("http://mydev.local/callback", List.of("mydev.local"));
    }

    @Test
    void shouldRejectHttpNonLoopbackRedirectUri() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri(
                "http://client.example.com/callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
    }

    @Test
    void shouldRejectCustomSchemeRedirectUri() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri("vscode://publisher.app/callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri("workbuddy://callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
    }

    @Test
    void shouldRejectRelativeRedirectUri() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri("/callback", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
    }

    @Test
    void shouldRejectRedirectUriWithFragment() {
        assertThatThrownBy(() -> McpCimdDocumentValidator.validateRedirectUri(
                "https://client.example.com/callback#section", LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
    }

}
