package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.mcp.oauth2.McpOAuthMetadataController;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.mcp.security.McpStrictAccessTokenFilter;
import cn.iocoder.yudao.framework.mcp.transport.McpProtocolDispatcherServlet;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.filter.CacheRequestBodyWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
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
                    // P0-1：modern transport 同样只有 disabled 注册，不对外暴露；
                    // 否则 Spring Boot 会自动给这个裸 Servlet bean 生成映射。
                    ServletRegistrationBean<?> modernRegistration = context.getBean(
                            YudaoMcpAutoConfiguration.MCP_MODERN_SERVLET_REGISTRATION_NAME,
                            ServletRegistrationBean.class);
                    assertThat(modernRegistration.isEnabled()).isFalse();
                    assertThat(modernRegistration.getServlet())
                            .isInstanceOf(HttpServletMcpTransport.class);
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldEnableLegacyByDefault() {
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> assertThat(context.getBean(YudaoMcpServerProperties.class)
                        .getLegacy().isEnabled()).isTrue());
    }

    @Test
    void shouldRejectLegacyInitializeWhenLegacyDisabled() throws Exception {
        // D4：开关只关分流入口、不拆 bean——legacy transport 与 stateless server
        // 仍在容器里，只是分流器不再把请求交给它们。
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}";
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.legacy.enabled=false")
                .run(context -> {
                    assertThat(context.getBean(YudaoMcpServerProperties.class)
                            .getLegacy().isEnabled()).isFalse();
                    assertThat(context).hasSingleBean(HttpServletStatelessServerTransport.class);
                    assertThat(context).hasSingleBean(McpStatelessSyncServer.class);
                    MockHttpServletResponse response = dispatch(
                            context, cachedJsonRequest(json));
                    assertThat(response.getStatus()).isEqualTo(400);
                    JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                    assertThat(payload.path("id").asInt()).isEqualTo(1);
                    assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
                    assertThat(payload.path("error").path("message").asText()).contains("disabled");
                });
    }

    @Test
    void shouldRejectLegacyToolsListWhenLegacyDisabled() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\",\"params\":{}}";
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.legacy.enabled=false")
                .run(context -> {
                    MockHttpServletResponse response = dispatch(
                            context, cachedJsonRequest(json));
                    assertThat(response.getStatus()).isEqualTo(400);
                    JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                    assertThat(payload.path("id").asInt()).isEqualTo(7);
                    assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
                });
    }

    @Test
    void shouldRejectLegacyToolsCallWhenLegacyDisabled() throws Exception {
        // D5 补齐：开关关闭时 legacy tools/call 同样在分流器里被拒绝，不进 ACF 执行链。
        String json = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"demo.echo\",\"arguments\":{}}}";
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.legacy.enabled=false")
                .run(context -> {
                    MockHttpServletResponse response = dispatch(
                            context, cachedJsonRequest(json));
                    assertThat(response.getStatus()).isEqualTo(400);
                    JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                    assertThat(payload.path("id").asInt()).isEqualTo(9);
                    assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
                    assertThat(payload.path("error").path("message").asText()).contains("disabled");
                });
    }

    @Test
    void shouldStillServeModernDiscoverWhenLegacyDisabled() throws Exception {
        // Modern 不受 legacy 开关影响：server/discover 走真实 modern transport，
        // 需先按 McpAuthenticatedTransportContextExtractor 的要求放一个 LoginUser。
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":"
                + "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true",
                        "yudao.mcp.server.legacy.enabled=false")
                .run(context -> {
                    MockHttpServletRequest source = new MockHttpServletRequest();
                    source.setMethod("POST");
                    source.setRequestURI("/mcp");
                    source.setContentType("application/json");
                    source.setContent(json.getBytes(StandardCharsets.UTF_8));
                    source.addHeader("Mcp-Method", "server/discover");
                    source.addHeader("MCP-Protocol-Version", "2026-07-28");
                    LoginUser loginUser = new LoginUser();
                    loginUser.setId(10L);
                    loginUser.setTenantId(20L);
                    SecurityFrameworkUtils.setLoginUser(loginUser, source);
                    MockHttpServletResponse response = dispatch(
                            context, new CacheRequestBodyWrapper(source));
                    assertThat(response.getStatus()).isEqualTo(200);
                    JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                    assertThat(payload.path("result").path("resultType").asText())
                            .isEqualTo("complete");
                    assertThat(payload.path("result").path("supportedVersions").toString())
                            .isEqualTo("[\"2026-07-28\"]");
                });
    }

    @Test
    void shouldRejectLegacyInitializeDirectlyOnModernTransport() throws Exception {
        // P1-2：绕过分流器、直驱 modern transport，锁定 initialize → -32022。
        // D5 把 /mcp 上的 legacy initialize 改为接受后，全仓不再有测试覆盖
        // modern transport 自己对 initialize 的拒绝（分流器自造的 kill-switch
        // -32022 不算，那不是 modern transport 发出的）。直驱时 transport 的
        // 头校验豁免无 _meta 版本的 initialize（SDK HttpServletMcpTransport），
        // 由 McpServer 以 -32022（HTTP 400）拒绝。
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}";
        contextRunner.withPropertyValues("yudao.mcp.server.enabled=true")
                .run(context -> {
                    LoginUser loginUser = new LoginUser();
                    loginUser.setId(10L);
                    loginUser.setTenantId(20L);
                    SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
                    HttpServletMcpTransport transport = context.getBean(HttpServletMcpTransport.class);
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setMethod("POST");
                    request.setRequestURI("/mcp");
                    request.setContentType("application/json");
                    request.addHeader("Accept", "application/json, text/event-stream");
                    request.setContent(json.getBytes(StandardCharsets.UTF_8));
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    transport.service((ServletRequest) request, (ServletResponse) response);
                    assertThat(response.getStatus()).isEqualTo(400);
                    JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                    assertThat(payload.path("id").asInt()).isEqualTo(1);
                    assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
                    assertThat(payload.path("error").path("message").asText())
                            .contains("Unsupported protocol version");
                });
    }

    /**
     * 直接调注册在 /mcp 上的分流器（GenericServlet 的 public service 重载，
     * protected 的 HttpServlet 重载在 config 包调不到）。
     */
    private static MockHttpServletResponse dispatch(
            org.springframework.context.ApplicationContext context,
            jakarta.servlet.http.HttpServletRequest request) throws Exception {
        McpProtocolDispatcherServlet dispatcher = (McpProtocolDispatcherServlet) context.getBean(
                        YudaoMcpAutoConfiguration.MCP_SERVLET_NAME, ServletRegistrationBean.class)
                .getServlet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        dispatcher.service((ServletRequest) request, (ServletResponse) response);
        return response;
    }

    private static jakarta.servlet.http.HttpServletRequest cachedJsonRequest(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/mcp");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return new CacheRequestBodyWrapper(request);
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
