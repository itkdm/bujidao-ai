package cn.iocoder.yudao.module.mcp.framework.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.mcp.framework.security.McpScopeAuthorizationFilter;
import cn.iocoder.yudao.module.mcp.framework.security.McpTenantContextFilter;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YudaoMcpServerAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    YudaoMcpServerAutoConfiguration.class))
            .withBean(CapabilityToolCatalog.class, () -> mock(CapabilityToolCatalog.class))
            .withBean(CapabilityToolInvoker.class, () -> mock(CapabilityToolInvoker.class))
            .withBean(TokenAuthenticationFilter.class, () -> mock(TokenAuthenticationFilter.class))
            .withBean("mcpSecurityFilterChain", SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
            .withBean(SecurityProperties.class, SecurityProperties::new)
            .withBean(WebProperties.class, YudaoMcpServerAutoConfigurationTest::createWebProperties)
            .withPropertyValues("yudao.web.admin-ui.url=http://localhost");

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
            assertThat(context).doesNotHaveBean(HttpServletStatelessServerTransport.class);
        });
    }

    @Test
    void shouldRegisterStatelessServerWhenEnabled() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
                    assertThat(context).hasSingleBean(HttpServletStatelessServerTransport.class);
                    assertThat(context).hasSingleBean(YudaoMcpServerProperties.class);
                    assertThat(context).hasSingleBean(YudaoMcpToolProperties.class);
                    assertThat(context).hasSingleBean(McpTransportContextExtractor.class);
                    assertThat(context).hasSingleBean(ServerTransportSecurityValidator.class);
                    assertThat(context).hasSingleBean(McpTenantContextFilter.class);
                    assertThat(context).hasSingleBean(McpScopeAuthorizationFilter.class);
                    assertThat(context.getBean(YudaoMcpServerProperties.class).getRequiredScopes())
                            .containsExactly("mcp:access");
                    assertThat(context.getBean(YudaoMcpServerAutoConfiguration.MCP_QUERY_TOKEN_FILTER_NAME))
                            .isInstanceOf(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
                    assertThat(context.getBean(YudaoMcpServerAutoConfiguration.MCP_REQUEST_SIZE_FILTER_NAME))
                            .isInstanceOf(org.springframework.boot.web.servlet.FilterRegistrationBean.class);
                });
    }

    @Test
    void shouldValidateAllowedOriginAndHost() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> {
                    ServerTransportSecurityValidator validator = context.getBean(ServerTransportSecurityValidator.class);
                    validator.validateHeaders(java.util.Map.of(
                            "Origin", java.util.List.of("http://localhost:8080"),
                            "Host", java.util.List.of("localhost:8080")));

                    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> validator.validateHeaders(
                            java.util.Map.of("Origin", java.util.List.of("https://attacker.example"),
                                    "Host", java.util.List.of("localhost:8080")))))
                            .isInstanceOf(ServerTransportSecurityException.class)
                            .hasMessage("Invalid Origin header");
                    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> validator.validateHeaders(
                            java.util.Map.of("Host", java.util.List.of("attacker.example")))))
                            .isInstanceOf(ServerTransportSecurityException.class)
                            .hasMessage("Invalid Host header");
                });
    }

    @Test
    void shouldRejectInvalidEndpointConfiguration() {
        contextRunner.withPropertyValues(
                        "yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.endpoint=/**")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectInvalidRequestSizeConfiguration() {
        contextRunner.withPropertyValues(
                        "yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.max-request-size=0B")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectEmptyRequiredScopesConfiguration() {
        contextRunner.withPropertyValues(
                        "yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.required-scopes=")
                .run(context -> assertThat(context).hasFailed());
    }

    private static WebProperties createWebProperties() {
        WebProperties properties = new WebProperties();
        properties.setAdminUi(new WebProperties.Ui());
        return properties;
    }

}
