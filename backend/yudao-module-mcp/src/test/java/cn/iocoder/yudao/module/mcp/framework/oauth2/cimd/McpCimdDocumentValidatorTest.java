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
        assertThat(metadata.grantTypes()).containsExactly("authorization_code");
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
    void shouldRejectDocumentWithoutClientName() {
        Map<String, Object> document = validDocument();
        document.remove("client_name");

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldRejectDocumentWithBlankClientName() {
        Map<String, Object> document = validDocument();
        document.put("client_name", "   ");

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
    void shouldRejectPrivateKeyJwtWithoutJwks() {
        Map<String, Object> document = validDocument();
        document.put("token_endpoint_auth_method", "private_key_jwt");

        assertThatThrownBy(() -> McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
    }

    @Test
    void shouldAcceptPrivateKeyJwtWithJwksUri() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.put("token_endpoint_auth_method", "private_key_jwt");
        document.put("jwks_uri", "https://client.example.com/jwks.json");

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        assertThat(metadata.tokenEndpointAuthMethod()).isEqualTo("private_key_jwt");
        assertThat(metadata.jwksUri()).isEqualTo("https://client.example.com/jwks.json");
    }

    @Test
    void shouldAcceptPrivateKeyJwtWithJwksObject() throws McpCimdException {
        Map<String, Object> document = validDocument();
        document.put("token_endpoint_auth_method", "private_key_jwt");
        document.put("jwks", Map.of("keys", List.of()));

        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                "https://client.example.com/metadata", document, LOCAL_HOSTS);

        assertThat(metadata.jwks()).contains("keys");
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
