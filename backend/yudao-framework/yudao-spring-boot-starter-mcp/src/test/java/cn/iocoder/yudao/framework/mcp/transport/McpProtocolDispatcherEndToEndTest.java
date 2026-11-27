package cn.iocoder.yudao.framework.mcp.transport;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.mcp.McpTestFixtures;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolProtocolMetadata;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpAcfAutoConfiguration;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpAutoConfiguration;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.filter.CacheRequestBodyWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.dispatch;
import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.toolNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 分流器级端到端：真实分流器 + 真实 legacy transport / stateless server + mock 的
 * ACF 能力目录（17 个 ERP 工具，与
 * {@code McpDualProtocolIntegrationTest} 同规模），modern transport 用 mock 代替。
 *
 * <p>补的是原始 HTTP 集成测试给不了的断言：legacy 全流程（initialize →
 * tools/list → tools/call）对 modern transport 零交互。无可见性服务 bean 时
 * legacy tools/list 不过滤，返回全部 17 个（与有可见性服务的 HTTP 测试里的 16 个
 * 对照，过滤只在有服务时发生）。</p>
 *
 * <p>本文件在本机未跑过（Maven 无法运行），需在能跑 Maven 的环境重跑确认。</p>
 */
class McpProtocolDispatcherEndToEndTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ORDER_CREATE_TOOL = "erp.order.create";

    private static final List<String> ALL_TOOL_NAMES = List.of(
            "erp.order.create", "erp.order.query", "erp.order.cancel",
            "erp.product.create", "erp.product.query", "erp.product.update",
            "erp.stock.query", "erp.stock.adjust",
            "erp.purchase.create", "erp.purchase.approve",
            "erp.sale.create",
            "erp.finance.invoice.create", "erp.finance.payment.query",
            "erp.customer.create", "erp.customer.query",
            "erp.payment.refund", "erp.ledger.close");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldServeLegacyEndToEndWithoutTouchingModernTransport() throws Exception {
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
        List<CapabilityToolDescriptor> descriptors = ALL_TOOL_NAMES.stream()
                .map(McpTestFixtures::descriptor)
                .toList();
        when(catalog.listDeclared()).thenReturn(descriptors);
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        when(invoker.invoke(any())).thenAnswer(invocation -> {
            CapabilityToolCall call = invocation.getArgument(0);
            return CapabilityResult.success(call.getCapabilityName(), (Object) "hello")
                    .withTraceId("trace-dispatch");
        });
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        YudaoMcpAutoConfiguration.class, YudaoMcpAcfAutoConfiguration.class))
                .withBean(CapabilityToolCatalog.class, () -> catalog)
                .withBean(CapabilityToolInvoker.class, () -> invoker)
                .withBean(OAuth2TokenCommonApi.class, () -> mock(OAuth2TokenCommonApi.class))
                .withBean(TokenAuthenticationFilter.class, () -> mock(TokenAuthenticationFilter.class))
                .withBean("mcpSecurityFilterChain", SecurityFilterChain.class,
                        () -> mock(SecurityFilterChain.class))
                .withBean(SecurityProperties.class, SecurityProperties::new)
                .withBean(WebProperties.class, McpProtocolDispatcherEndToEndTest::createWebProperties)
                .withBean(HttpServletMcpTransport.class, () -> modernTransport)
                .withPropertyValues("yudao.web.admin-ui.url=http://localhost",
                        "yudao.mcp.server.enabled=true");

        runner.run(context -> {
            LoginUser loginUser = new LoginUser();
            loginUser.setId(10L);
            loginUser.setTenantId(20L);
            // 默认的 McpAuthenticatedTransportContextExtractor 要求 SecurityContext
            // 里有 LoginUser，否则直接抛错；分流前按已有测试的做法先放进去。
            SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());

            MockHttpServletResponse initializeResponse = dispatch(context,
                    legacyRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                            + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                            + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}"));
            assertThat(initializeResponse.getStatus()).isEqualTo(200);
            JsonNode initPayload = OBJECT_MAPPER.readTree(initializeResponse.getContentAsString());
            assertThat(initPayload.path("result").path("protocolVersion").asText())
                    .isEqualTo("2025-11-25");
            assertThat(initPayload.path("result").path("serverInfo").path("name").asText())
                    .isEqualTo("bujidao-mcp-server");

            MockHttpServletResponse listResponse = dispatch(context,
                    legacyRequest("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"));
            assertThat(listResponse.getStatus()).isEqualTo(200);
            List<String> names = toolNames(OBJECT_MAPPER.readTree(listResponse.getContentAsString()));
            assertThat(names).hasSize(17).contains(ORDER_CREATE_TOOL, "erp.ledger.close");
            assertThat(listResponse.getContentAsString()).doesNotContain("acf.confirm");

            MockHttpServletResponse callResponse = dispatch(context,
                    legacyRequest("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":"
                            + "{\"name\":\"" + ORDER_CREATE_TOOL
                            + "\",\"arguments\":{\"message\":\"hello\"}}}"));
            assertThat(callResponse.getStatus()).isEqualTo(200);
            JsonNode callResult = OBJECT_MAPPER.readTree(callResponse.getContentAsString())
                    .path("result");
            assertThat(callResult.path("isError").asBoolean()).isFalse();
            assertThat(callResult.path("structuredContent").path("result").asText())
                    .isEqualTo("hello");
            assertThat(callResult.path("_meta").path(AcfMcpToolProtocolMetadata.TRACE_ID).asText())
                    .isEqualTo("trace-dispatch");

            // Legacy 全程不经过 modern：mock 的 modern transport 一次也没被调到。
            verifyNoInteractions(modernTransport);
        });
    }

    /**
     * 老客户端的请求：无 modern 头，Accept 必须同时含 application/json 与
     * text/event-stream（legacy transport 的 doPost 要求），requestURI 必须是
     * /mcp（legacy transport 的 doPost 按 URI 末尾匹配端点）。
     */
    private static HttpServletRequest legacyRequest(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/mcp");
        request.setContentType("application/json");
        request.addHeader("Accept", "application/json, text/event-stream");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return new CacheRequestBodyWrapper(request);
    }

    private static WebProperties createWebProperties() {
        WebProperties properties = new WebProperties();
        properties.setAdminUi(new WebProperties.Ui());
        return properties;
    }

}
