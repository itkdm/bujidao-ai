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
        // CIMD 与 DCR 并存，CIMD 优先；元数据必须声明 CIMD 支持。
        assertThat(metadata.get("client_id_metadata_document_supported")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldExposeRegistrationEndpointOnlyWhenEnabled() {
        // 默认关闭：元数据不含 registration_endpoint（只藏入口，端点本身由
        // yudao.mcp.oauth.dynamic-client-registration-enabled 控制）。
        Map<String, Object> defaultMetadata = new McpOAuthMetadataController(
                new YudaoMcpServerProperties(), emptyBeanProvider()).authorizationServerMetadata(request());

        assertThat(defaultMetadata).doesNotContainKey("registration_endpoint");
        // 默认配置下也必须声明 CIMD 支持：真实客户端先读元数据再决定注册方式，
        // 只在“配了各端点”的测试里断言，默认路径丢了标志测试仍会全绿。
        assertThat(defaultMetadata.get("client_id_metadata_document_supported")).isEqualTo(Boolean.TRUE);

        // 开关开启：默认注册端点为 admin-api 前缀 + /mcp/oauth2/register。
        YudaoMcpServerProperties enabledProperties = new YudaoMcpServerProperties();
        enabledProperties.setDynamicClientRegistrationEnabled(true);
        Map<String, Object> enabledMetadata = new McpOAuthMetadataController(
                enabledProperties, emptyBeanProvider()).authorizationServerMetadata(request());

        assertThat(enabledMetadata.get("registration_endpoint"))
                .isEqualTo("https://erp.example.com/admin-api/mcp/oauth2/register");
        assertThat(enabledMetadata.get("client_id_metadata_document_supported")).isEqualTo(Boolean.TRUE);

        // 自定义注册端点覆盖默认值。
        YudaoMcpServerProperties customProperties = new YudaoMcpServerProperties();
        customProperties.setDynamicClientRegistrationEnabled(true);
        customProperties.setRegistrationEndpoint("https://auth.example.com/oauth2/register");
        Map<String, Object> customMetadata = new McpOAuthMetadataController(
                customProperties, emptyBeanProvider()).authorizationServerMetadata(request());

        assertThat(customMetadata.get("registration_endpoint"))
                .isEqualTo("https://auth.example.com/oauth2/register");
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
