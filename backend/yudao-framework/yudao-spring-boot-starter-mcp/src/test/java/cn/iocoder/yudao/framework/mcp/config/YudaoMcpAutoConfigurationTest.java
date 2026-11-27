package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.mcp.oauth2.McpOAuthMetadataController;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.mcp.security.McpStrictAccessTokenFilter;
import cn.iocoder.yudao.framework.mcp.transport.McpProtocolDispatcherServlet;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.mcp.security.McpScopeAuthorizationFilter;
import cn.iocoder.yudao.framework.mcp.security.McpTenantContextFilter;
import io.modelcontextprotocol.modern.server.HmacRequestStateCodec;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.RequestStateCodec;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HeaderAccessor;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class YudaoMcpAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    YudaoMcpAutoConfiguration.class, YudaoMcpAcfAutoConfiguration.class))
            .withBean(CapabilityToolCatalog.class, () -> mock(CapabilityToolCatalog.class))
            .withBean(CapabilityToolInvoker.class, () -> mock(CapabilityToolInvoker.class))
            .withBean(OAuth2TokenCommonApi.class, () -> mock(OAuth2TokenCommonApi.class))
            .withBean(TokenAuthenticationFilter.class, () -> mock(TokenAuthenticationFilter.class))
            .withBean("mcpSecurityFilterChain", SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
            .withBean(SecurityProperties.class, SecurityProperties::new)
            .withBean(WebProperties.class, YudaoMcpAutoConfigurationTest::createWebProperties)
            .withPropertyValues("yudao.web.admin-ui.url=http://localhost");

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(McpServer.class);
            assertThat(context).doesNotHaveBean(HttpServletMcpTransport.class);
            assertThat(context).doesNotHaveBean(McpStatelessSyncServer.class);
            assertThat(context).doesNotHaveBean(HttpServletStatelessServerTransport.class);
        });
    }

    @Test
    void shouldRegisterModernServerWhenEnabled() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpServer.class);
                    assertThat(context).hasSingleBean(HttpServletMcpTransport.class);
                    assertThat(context).hasSingleBean(RequestStateCodec.class);
                    assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
                    assertThat(context.getBean("mcpStatelessSyncServer"))
                            .isInstanceOf(McpStatelessSyncServer.class);
                    assertThat(context).hasSingleBean(HttpServletStatelessServerTransport.class);
                    // D3：/mcp 改挂分流器（注册名与映射不变），分流器再转发给两条腿；
                    // legacy transport 仍只有 disabled 注册，不对外暴露。
                    ServletRegistrationBean<?> legacyRegistration = context.getBean(
                            YudaoMcpAutoConfiguration.MCP_LEGACY_SERVLET_REGISTRATION_NAME,
                            ServletRegistrationBean.class);
                    assertThat(legacyRegistration.isEnabled()).isFalse();
                    assertThat(context.getBean(YudaoMcpAutoConfiguration.MCP_SERVLET_NAME,
                            ServletRegistrationBean.class).getServlet())
                            .isInstanceOf(McpProtocolDispatcherServlet.class);
                    assertThat(context.getBean(RequestStateCodec.class))
                            .isInstanceOf(HmacRequestStateCodec.class);
                    assertThat(context).hasSingleBean(ServerHttpHeaderValidator.class);
                    assertThat(context.getBean(ServerHttpHeaderValidator.class))
                            .isInstanceOf(DefaultServerTransportSecurityValidator.class);
                    assertThat(context).hasSingleBean(YudaoMcpServerProperties.class);
                    assertThat(context).hasSingleBean(YudaoMcpAcfProperties.class);
                    assertThat(context).hasSingleBean(McpOAuthMetadataController.class);
                    assertThat(context).hasSingleBean(McpTransportContextExtractor.class);
                    assertThat(context).hasSingleBean(McpTenantContextFilter.class);
                    assertThat(context).hasSingleBean(McpScopeAuthorizationFilter.class);
                    assertThat(context).hasSingleBean(McpStrictAccessTokenFilter.class);
                    assertThat(context.getBean(YudaoMcpServerProperties.class).getRequiredScopes())
                            .containsExactly("mcp:access");
                    assertThat(context.getBean(YudaoMcpAutoConfiguration.MCP_QUERY_TOKEN_FILTER_NAME))
                            .isInstanceOf(FilterRegistrationBean.class);
                    assertThat(context.getBean(YudaoMcpAutoConfiguration.MCP_REQUEST_SIZE_FILTER_NAME))
                            .isInstanceOf(FilterRegistrationBean.class);
                    assertThat(context.getBean(YudaoMcpAutoConfiguration.MCP_REQUEST_BODY_CHARSET_FILTER_NAME))
                            .isInstanceOf(FilterRegistrationBean.class);

                    FilterRegistrationBean<?> strictAccessTokenFilterRegistration = context.getBean(
                            YudaoMcpAutoConfiguration.MCP_STRICT_ACCESS_TOKEN_FILTER_REGISTRATION_NAME,
                            FilterRegistrationBean.class);
                    FilterRegistrationBean<?> scopeAuthorizationFilterRegistration = context.getBean(
                            YudaoMcpAutoConfiguration.MCP_SCOPE_AUTHORIZATION_FILTER_REGISTRATION_NAME,
                            FilterRegistrationBean.class);
                    FilterRegistrationBean<?> tenantContextFilterRegistration = context.getBean(
                            YudaoMcpAutoConfiguration.MCP_TENANT_CONTEXT_FILTER_REGISTRATION_NAME,
                            FilterRegistrationBean.class);
                    assertThat(strictAccessTokenFilterRegistration.isEnabled()).isFalse();
                    assertThat(scopeAuthorizationFilterRegistration.isEnabled()).isFalse();
                    assertThat(tenantContextFilterRegistration.isEnabled()).isFalse();
                });
    }

    @Test
    void shouldValidateAllowedOriginAndHost() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> {
                    ServerHttpHeaderValidator validator = context.getBean(ServerHttpHeaderValidator.class);
                    // 合法 Origin + Host：不抛异常。
                    validator.validate(headerAccessor(Map.of(
                            "Origin", List.of("http://localhost:8080"),
                            "Host", List.of("localhost:8080"))));

                    assertThatThrownBy(() -> validator.validate(headerAccessor(Map.of(
                            "Origin", List.of("https://attacker.example"),
                            "Host", List.of("localhost:8080")))))
                            .isInstanceOf(ServerTransportSecurityException.class)
                            .hasMessage("Invalid Origin header")
                            .satisfies(exception -> assertThat(
                                    ((ServerTransportSecurityException) exception).getStatusCode())
                                    .isEqualTo(403));
                    assertThatThrownBy(() -> validator.validate(headerAccessor(Map.of(
                            "Host", List.of("attacker.example")))))
                            .isInstanceOf(ServerTransportSecurityException.class)
                            .hasMessage("Invalid Host header")
                            .satisfies(exception -> assertThat(
                                    ((ServerTransportSecurityException) exception).getStatusCode())
                                    .isEqualTo(421));
                });
    }

    @Test
    void shouldRegisterRequestStateCodecWithSharedKey() {
        // 32 个零字节的 Base64：AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.request-state.shared-key="
                                + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .run(context -> assertThat(context).hasSingleBean(RequestStateCodec.class));
    }

    @Test
    void shouldRejectShortRequestStateSharedKey() {
        // 16 字节，不足 32 字节下限。
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.request-state.shared-key=AAAAAAAAAAAAAAAAAAAAAA==")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonBase64RequestStateSharedKey() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.request-state.shared-key=not-base64!!!")
                .run(context -> assertThat(context).hasFailed());
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

    private static HeaderAccessor headerAccessor(Map<String, List<String>> headers) {
        return new HeaderAccessor() {
            @Override
            public List<String> getHeader(String name) {
                return headers.entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(List.of());
            }

            @Override
            public List<String> getHeaderNames() {
                return List.copyOf(headers.keySet());
            }
        };
    }

    private static WebProperties createWebProperties() {
        WebProperties properties = new WebProperties();
        properties.setAdminUi(new WebProperties.Ui());
        return properties;
    }

}
