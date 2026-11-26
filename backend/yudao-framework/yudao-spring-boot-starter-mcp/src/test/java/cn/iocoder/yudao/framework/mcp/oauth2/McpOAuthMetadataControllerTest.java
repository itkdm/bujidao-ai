package cn.iocoder.yudao.framework.mcp.oauth2;

import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpOAuthMetadataControllerTest {

    @Test
    void shouldBuildProtectedResourceMetadataFromRequestByDefault() {
        McpOAuthMetadataController controller = new McpOAuthMetadataController(
                new YudaoMcpServerProperties(), emptyBeanProvider());
        MockHttpServletRequest request = request();

        Map<String, Object> metadata = controller.protectedResourceMetadata(request);

        assertThat(metadata.get("resource")).isEqualTo("https://erp.example.com/mcp");
        assertThat(metadata.get("authorization_servers")).isEqualTo(List.of("https://erp.example.com"));
        assertThat(metadata.get("scopes_supported")).isEqualTo(List.of("mcp:access"));
        assertThat(metadata.get("bearer_methods_supported")).isEqualTo(List.of("header"));
    }

    @Test
    void shouldBuildAuthorizationServerMetadataWithConfiguredEndpoints() {
        YudaoMcpServerProperties properties = new YudaoMcpServerProperties();
        properties.setAuthorizationServerIssuer("https://auth.example.com");
        properties.setAuthorizationEndpoint("https://auth.example.com/sso");
        properties.setTokenEndpoint("https://auth.example.com/admin-api/system/oauth2/token");
        properties.setRevocationEndpoint("https://auth.example.com/admin-api/system/oauth2/token");

        Map<String, Object> metadata = new McpOAuthMetadataController(
                properties, emptyBeanProvider()).authorizationServerMetadata(request());

        assertThat(metadata.get("issuer")).isEqualTo("https://auth.example.com");
        assertThat(metadata.get("authorization_endpoint")).isEqualTo("https://auth.example.com/sso");
        assertThat(metadata.get("token_endpoint"))
                .isEqualTo("https://auth.example.com/admin-api/system/oauth2/token");
        assertThat(metadata).doesNotContainKey("registration_endpoint");
        assertThat(metadata.get("token_endpoint_auth_methods_supported"))
                .isEqualTo(List.of("none"));
        assertThat(metadata.get("code_challenge_methods_supported")).isEqualTo(List.of("S256"));
    }

    @Test
    void shouldNeverExposeRegistrationEndpoint() {
        // DCR 已移除：YudaoMcpServerProperties 不再有注册开关 / 注册端点配置，
        // 元数据默认也不含 registration_endpoint。
        Map<String, Object> metadata = new McpOAuthMetadataController(
                new YudaoMcpServerProperties(), emptyBeanProvider()).authorizationServerMetadata(request());

        assertThat(metadata).doesNotContainKey("registration_endpoint");
    }

    @Test
    void shouldUseAdminUiAndAdminApiPrefixesWhenWebPropertiesAvailable() {
        WebProperties webProperties = new WebProperties();
        webProperties.setAdminUi(new WebProperties.Ui());
        webProperties.getAdminUi().setUrl("https://ui.example.com");
        webProperties.setAdminApi(new WebProperties.Api("/console-api", "**.controller.admin.**"));
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("webProperties", webProperties);

        Map<String, Object> metadata = new McpOAuthMetadataController(
                new YudaoMcpServerProperties(),
                beanFactory.getBeanProvider(WebProperties.class)).authorizationServerMetadata(request());

        assertThat(metadata.get("authorization_endpoint")).isEqualTo("https://ui.example.com/mcp/sso");
        assertThat(metadata.get("token_endpoint"))
                .isEqualTo("https://erp.example.com/console-api/mcp/oauth2/token");
        assertThat(metadata.get("revocation_endpoint"))
                .isEqualTo("https://erp.example.com/console-api/mcp/oauth2/revoke");
    }

    @Test
    void shouldNotExposeRegistrationEndpointWithConfiguredEndpoints() {
        // DCR 已移除：即使配了 issuer / 各端点，元数据也不含 registration_endpoint。
        YudaoMcpServerProperties properties = new YudaoMcpServerProperties();
        properties.setAuthorizationServerIssuer("https://auth.example.com");
        properties.setAuthorizationEndpoint("https://auth.example.com/sso");
        properties.setTokenEndpoint("https://auth.example.com/admin-api/system/oauth2/token");
        properties.setRevocationEndpoint("https://auth.example.com/admin-api/system/oauth2/token");

        Map<String, Object> metadata = new McpOAuthMetadataController(
                properties, emptyBeanProvider()).authorizationServerMetadata(request());

        assertThat(metadata.get("issuer")).isEqualTo("https://auth.example.com");
        assertThat(metadata.get("authorization_endpoint")).isEqualTo("https://auth.example.com/sso");
        assertThat(metadata).doesNotContainKey("registration_endpoint");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/.well-known/oauth-protected-resource");
        request.setScheme("https");
        request.setServerName("erp.example.com");
        request.setServerPort(443);
        return request;
    }

    private static org.springframework.beans.factory.ObjectProvider<WebProperties> emptyBeanProvider() {
        return new DefaultListableBeanFactory().getBeanProvider(WebProperties.class);
    }

}
