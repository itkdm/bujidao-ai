package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityVisibilityService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolProtocolMetadata;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.descriptor;
import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.toolNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 双协议集成测试：同一 /mcp endpoint 上 modern（2026-07-28）与 legacy（2025-11-25）
 * 两条腿用原始 HTTP 交替走完。
 *
 * <p>本文件在本机未跑过（Maven 无法运行），需在能跑 Maven 的环境重跑确认。</p>
 *
 * <p>目录是 17 个 ERP 能力（与生产 17 个 ERP 工具的规模一致）：其中
 * {@code erp.ledger.close} 只对 tools/list 隐藏（仍可按名调用，过滤只作用
 * 于列表），{@code erp.payment.refund} 是需要确认的能力（legacy 腿调它必须以
 * CONFIRMATION_UNAVAILABLE 明确拒绝，不执行、不复活 acf.confirm）。两腿共用
 * 同一份可见性判定：modern 腿经 {@code CapabilityToolExportService} 过滤，
 * legacy 腿经 addToolFilter 过滤，同一用户下两腿的 tools/list 均为 16 个
 *（隐藏同一工具）；过滤只作用于列表，find / tools/call 不过滤，两腿都能按名
 * 调到被隐藏的工具。</p>
 */
@SpringBootTest(classes = McpDualProtocolIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"yudao.mcp.server.enabled=true",
                "yudao.web.admin-ui.url=http://localhost"})
class McpDualProtocolIntegrationTest {

    private static final String LEGACY_PROTOCOL_VERSION = "2025-11-25";
    private static final String MODERN_PROTOCOL_VERSION = "2026-07-28";

    private static final String ORDER_CREATE_TOOL = "erp.order.create";
    private static final String HIDDEN_TOOL = "erp.ledger.close";
    private static final String GATED_TOOL = "erp.payment.refund";

    private static final List<String> ALL_TOOL_NAMES = List.of(
            "erp.order.create", "erp.order.query", "erp.order.cancel",
            "erp.product.create", "erp.product.query", "erp.product.update",
            "erp.stock.query", "erp.stock.adjust",
            "erp.purchase.create", "erp.purchase.approve",
            "erp.sale.create",
            "erp.finance.invoice.create", "erp.finance.payment.query",
            "erp.customer.create", "erp.customer.query",
            "erp.payment.refund", "erp.ledger.close");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearMockInvocations() {
        // 同一个 @SpringBootTest 上下文在方法间复用，手工 mock 的 bean 不像
        // @MockBean 那样在方法间自动重置：invoker / visibilityService 的调用
        // 计数会跨方法累积，后跑的 verify(times(n)) 会数到前面方法的调用。
        // 只清调用记录、不清桩（Mockito.reset 会连 @Bean 方法里设好的桩一起
        // 抹掉，invoker 之后就返回 null；@DirtiesContext(methodMode =
        // BEFORE_METHOD) 则每个方法都重启整个随机端口容器，太重）。桩保留、
        // 计数归零，各方法的 times(n) 只数本方法内的调用。
        clearInvocations(applicationContext.getBean(CapabilityToolInvoker.class),
                applicationContext.getBean(CapabilityVisibilityService.class));
    }

    @Test
    void shouldCompleteLegacyHandshakeListAndCallOverRawHttp() throws Exception {
        ResponseEntity<String> initialize = post("/mcp",
                jsonRpc(1, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}"),
                legacyHeaders());

        assertThat(initialize.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode initPayload = objectMapper.readTree(initialize.getBody());
        assertThat(initPayload.path("result").path("protocolVersion").asText())
                .isEqualTo(LEGACY_PROTOCOL_VERSION);
        assertThat(initPayload.path("result").path("serverInfo").path("name").asText())
                .isEqualTo("bujidao-mcp-server");
        assertThat(initPayload.path("result").path("capabilities").path("tools").isObject()).isTrue();
        // Legacy 握手全程不经过 modern：响应里不应出现 modern 协议版本。
        assertThat(initialize.getBody()).doesNotContain(MODERN_PROTOCOL_VERSION);

        ResponseEntity<String> initialized = post("/mcp",
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}",
                legacyHeaders());
        assertThat(initialized.getStatusCode().value()).isEqualTo(202);

        ResponseEntity<String> list = post("/mcp", jsonRpc(2, "tools/list", "{}"), legacyHeaders());
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        List<String> legacyNames = toolNames(objectMapper.readTree(list.getBody()));
        assertThat(legacyNames).hasSize(16)
                .doesNotContain(HIDDEN_TOOL)
                .contains(ORDER_CREATE_TOOL, GATED_TOOL);
        assertThat(list.getBody()).doesNotContain("acf.confirm");

        ResponseEntity<String> call = post("/mcp",
                jsonRpc(3, "tools/call", "{\"name\":\"" + ORDER_CREATE_TOOL
                        + "\",\"arguments\":{\"message\":\"hello\"}}"),
                legacyHeaders());
        assertThat(call.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode callResult = objectMapper.readTree(call.getBody()).path("result");
        assertThat(callResult.path("isError").asBoolean()).isFalse();
        assertThat(callResult.path("structuredContent").path("result").asText()).isEqualTo("hello");
        assertThat(callResult.path("_meta").path(AcfMcpToolProtocolMetadata.TRACE_ID).asText())
                .isEqualTo("trace-dual");
        assertThat(callResult.path("_meta").path(AcfMcpToolProtocolMetadata.STATUS).asText())
                .isEqualTo("SUCCESS");

        // 被列表隐藏的工具仍可按名调用：过滤只作用于 tools/list，调用时由 ACF 执行链校验。
        ResponseEntity<String> hiddenCall = post("/mcp",
                jsonRpc(4, "tools/call", "{\"name\":\"" + HIDDEN_TOOL
                        + "\",\"arguments\":{\"message\":\"hello\"}}"),
                legacyHeaders());
        assertThat(hiddenCall.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(hiddenCall.getBody()).path("result").path("isError")
                .asBoolean()).isFalse();

        // mock 交互计数：两次 tools/call 各调一次 invoker；一次 legacy tools/list
        // 对 17 个声明工具各调一次 getVisible（过滤逐工具判定，不过滤调用）。
        verify(applicationContext.getBean(CapabilityToolInvoker.class), times(2))
                .invoke(any());
        verify(applicationContext.getBean(CapabilityVisibilityService.class), times(17))
                .getVisible(anyString(), any());
    }

    @Test
    void shouldServeModernAndLegacyInterleavedWithoutInterference() throws Exception {
        ResponseEntity<String> discover = post("/mcp",
                """
                {
                  "jsonrpc": "2.0",
                  "id": 10,
                  "method": "server/discover",
                  "params": {
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                      "io.modelcontextprotocol/clientCapabilities": {}
                    }
                  }
                }
                """,
                modernHeaders("server/discover"));
        assertThat(discover.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode discoverResult = objectMapper.readTree(discover.getBody()).path("result");
        assertThat(discoverResult.path("resultType").asText()).isEqualTo("complete");
        assertThat(discoverResult.path("supportedVersions").toString()).isEqualTo("[\"2026-07-28\"]");

        ResponseEntity<String> initialize = post("/mcp",
                jsonRpc(11, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}"),
                legacyHeaders());
        assertThat(initialize.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(initialize.getBody()).path("result")
                .path("protocolVersion").asText()).isEqualTo(LEGACY_PROTOCOL_VERSION);

        ResponseEntity<String> modernList = post("/mcp",
                """
                {"jsonrpc":"2.0","id":12,"method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """,
                modernHeaders("tools/list"));
        assertThat(modernList.getStatusCode().is2xxSuccessful()).isTrue();
        // 两腿共用同一份可见性判定：modern 经 export service 过滤，legacy 经
        // addToolFilter 过滤，同一用户下隐藏同一工具，两腿均为 16 个。
        assertThat(toolNames(objectMapper.readTree(modernList.getBody()))).hasSize(16)
                .doesNotContain(HIDDEN_TOOL)
                .contains(ORDER_CREATE_TOOL, GATED_TOOL);

        ResponseEntity<String> legacyList = post("/mcp", jsonRpc(13, "tools/list", "{}"),
                legacyHeaders());
        assertThat(legacyList.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(toolNames(objectMapper.readTree(legacyList.getBody()))).hasSize(16)
                .doesNotContain(HIDDEN_TOOL)
                .contains(GATED_TOOL);

        ResponseEntity<String> modernCall = post("/mcp",
                """
                {"jsonrpc":"2.0","id":14,"method":"tools/call","params":{
                  "name":"erp.order.create","arguments":{"message":"hello"},
                  "_meta":{
                    "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                    "io.modelcontextprotocol/clientCapabilities":{}}
                }}
                """,
                modernHeaders("tools/call", ORDER_CREATE_TOOL));
        assertThat(modernCall.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode modernResult = objectMapper.readTree(modernCall.getBody()).path("result");
        assertThat(modernResult.path("isError").asBoolean()).isFalse();
        assertThat(modernResult.path("structuredContent").path("result").asText()).isEqualTo("hello");

        // 被列表隐藏的工具在 modern 腿上同样可按名调用：仓库的 find 不做可见性
        // 过滤，调用时由 ACF 执行链校验（与 legacy 腿的 hiddenCall 对照）。
        ResponseEntity<String> modernHiddenCall = post("/mcp",
                """
                {"jsonrpc":"2.0","id":15,"method":"tools/call","params":{
                  "name":"erp.ledger.close","arguments":{"message":"hello"},
                  "_meta":{
                    "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                    "io.modelcontextprotocol/clientCapabilities":{}}
                }}
                """,
                modernHeaders("tools/call", HIDDEN_TOOL));
        assertThat(modernHiddenCall.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(modernHiddenCall.getBody()).path("result")
                .path("isError").asBoolean()).isFalse();

        ResponseEntity<String> legacyCall = post("/mcp",
                jsonRpc(16, "tools/call", "{\"name\":\"" + ORDER_CREATE_TOOL
                        + "\",\"arguments\":{\"message\":\"hello\"}}"),
                legacyHeaders());
        assertThat(legacyCall.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode legacyResult = objectMapper.readTree(legacyCall.getBody()).path("result");
        assertThat(legacyResult.path("isError").asBoolean()).isFalse();
        assertThat(legacyResult.path("structuredContent").path("result").asText()).isEqualTo("hello");

        // mock 交互计数：三次 tools/call（modern 两次 + legacy 一次）各调一次
        // invoker；一次 legacy tools/list 对 17 个声明工具各调一次 getVisible。
        verify(applicationContext.getBean(CapabilityToolInvoker.class), times(3))
                .invoke(any());
        verify(applicationContext.getBean(CapabilityVisibilityService.class), times(17))
                .getVisible(anyString(), any());
    }

    @Test
    void shouldReturnConfirmationUnavailableForGatedToolOnLegacyLeg() throws Exception {
        ResponseEntity<String> initialize = post("/mcp",
                jsonRpc(20, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}"),
                legacyHeaders());
        assertThat(initialize.getStatusCode().is2xxSuccessful()).isTrue();

        // 需要确认的能力在 legacy 腿上以明确的错误结果返回：不执行、不 elicite、
        // 不复活 acf.confirm；challenge 摘要仍进 _meta 供排查，但不含 requestDigest。
        ResponseEntity<String> call = post("/mcp",
                jsonRpc(21, "tools/call", "{\"name\":\"" + GATED_TOOL
                        + "\",\"arguments\":{\"message\":\"refund\"}}"),
                legacyHeaders());
        assertThat(call.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode callResult = objectMapper.readTree(call.getBody()).path("result");
        assertThat(callResult.path("isError").asBoolean()).isTrue();
        assertThat(callResult.path("_meta").path(AcfMcpToolProtocolMetadata.ERROR_CODE).asText())
                .isEqualTo("CONFIRMATION_UNAVAILABLE");
        assertThat(callResult.path("_meta").path(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE)
                .path("challengeId").asText()).isEqualTo("challenge-legacy-001");
        assertThat(call.getBody()).doesNotContain("acf.confirm");
        assertThat(call.getBody()).doesNotContain("internal-digest");
        // HTTP 层也不应出现 requestDigest 键名（handler 层的结构化断言见单测，
        // 这里锁定整条 HTTP 响应体不含该键）。
        assertThat(call.getBody()).doesNotContain("requestDigest");

        // 需要确认的调用同样进 ACF 执行链一次（由 invoker 返回确认态），不是被
        // handler 提前拦下。
        verify(applicationContext.getBean(CapabilityToolInvoker.class), times(1))
                .invoke(any());
    }

    @Test
    void shouldNotLetModernTransportStealDispatcherServletMapping() {
        // P0-1：modern transport 是裸 HttpServlet bean，必须被 disabled 注册占住，
        // 否则 Spring Boot 会自动给它生成映射抢走 "/"，整个 Spring MVC 全部 404。
        // ApplicationContextRunner 测不到这个（没有真实容器），必须在 RANDOM_PORT
        // 真启动后断言：注册表里没有 mcpServerTransport，且 DispatcherServlet
        // 仍在；非 /mcp 请求仍由 DispatcherServlet 处理（无 handler → 404；
        // 若被 modern transport 抢走，它的 doGet 会答 405）。
        assertThat(servletContext.getServletRegistration("dispatcherServlet")).isNotNull();
        assertThat(servletContext.getServletRegistration("bujidaoMcpServlet")).isNotNull();
        assertThat(servletContext.getServletRegistration("mcpServerTransport")).isNull();
        // 与 modern transport 对称：legacy transport 同样只有 disabled 注册，
        // 不对外暴露——容器里没有它的映射（分流器直接调 transport bean），
        // 上下文里的注册 bean 处于 disabled 状态。
        assertThat(servletContext.getServletRegistration(
                YudaoMcpAutoConfiguration.MCP_LEGACY_SERVLET_REGISTRATION_NAME)).isNull();
        assertThat(applicationContext.getBean(
                YudaoMcpAutoConfiguration.MCP_LEGACY_SERVLET_REGISTRATION_NAME,
                ServletRegistrationBean.class).isEnabled()).isFalse();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/some-other-path", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void shouldReturnEmptyResourceListsAndRejectPromptsOnLegacyLeg() throws Exception {
        // P1-6：legacy 腿的 resources 能力是 resources(false, false)（非空，
        // 故 SDK 注册了 handler），但资源列表本身是空的 → resources/list 与
        // resources/templates/list 均回 200 空列表；prompts 未配能力，handler
        // 未注册 → -32601（注意 legacy transport 对已处理的 JSON-RPC 请求一
        // 律回 HTTP 200，错误只体现在信封里，与 modern 腿的 404 不同）。
        // 指南 L12 的说法（legacy resources/list 返回空列表）经 SDK 源码核实
        // 属实，无需修正。
        ResponseEntity<String> resources = post("/mcp",
                jsonRpc(30, "resources/list", "{}"), legacyHeaders());
        assertThat(resources.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode resourcesPayload = objectMapper.readTree(resources.getBody());
        assertThat(resourcesPayload.path("result").path("resources").isArray()).isTrue();
        assertThat(resourcesPayload.path("result").path("resources")).isEmpty();

        ResponseEntity<String> templates = post("/mcp",
                jsonRpc(31, "resources/templates/list", "{}"), legacyHeaders());
        assertThat(templates.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode templatesPayload = objectMapper.readTree(templates.getBody());
        assertThat(templatesPayload.path("result").path("resourceTemplates").isArray()).isTrue();
        assertThat(templatesPayload.path("result").path("resourceTemplates")).isEmpty();

        ResponseEntity<String> prompts = post("/mcp",
                jsonRpc(32, "prompts/list", "{}"), legacyHeaders());
        assertThat(prompts.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode promptsPayload = objectMapper.readTree(prompts.getBody());
        assertThat(promptsPayload.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(promptsPayload.path("error").path("message").asText())
                .contains("Method not found: prompts/list");
    }

    @Test
    void shouldAnswerGetWith405OnLegacyLeg() {
        // 已纠正的验收标准：legacy stateless 没有 GET SSE 流，doGet 直接答 405，
        // 与 modern 相同、与 main 的行为一致；WorkBuddy 连 main 成功靠的是 POST
        // initialize，不是 GET。
        // 注意：本类的 mcpSecurityFilterChain 是空实现的 mock，本用例只覆盖
        // “GET 分流到 legacy 并答 405”这一路由行为，不覆盖认证；“GET 走 legacy
        // 也要认证”由 McpEndpointSecurityIntegrationTest 的真实链用例锁定。
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/mcp", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
    }

    private ResponseEntity<String> post(String path, String body, HttpHeaders headers) {
        return restTemplate.postForEntity("http://127.0.0.1:" + port + path,
                new HttpEntity<>(body, headers), String.class);
    }

    private static String jsonRpc(int id, String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method
                + "\",\"params\":" + params + "}";
    }

    /**
     * 老客户端的请求头：只有 Content-Type 与 Accept，没有任何 modern 头
     *（无 Mcp-Method、无 MCP-Protocol-Version）。Accept 必须同时含
     * application/json 与 text/event-stream，否则 legacy transport 以 400 拒绝。
     */
    private static HttpHeaders legacyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        return headers;
    }

    private static HttpHeaders modernHeaders(String method) {
        return modernHeaders(method, null);
    }

    private static HttpHeaders modernHeaders(String method, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("Mcp-Method", method);
        headers.set("MCP-Protocol-Version", MODERN_PROTOCOL_VERSION);
        if (name != null) {
            headers.set("Mcp-Name", name);
        }
        return headers;
    }

    private static CapabilityConfirmationChallenge confirmationChallenge() {
        return CapabilityConfirmationChallenge.builder()
                .challengeId("challenge-legacy-001")
                .capabilityName(GATED_TOOL)
                .capabilityVersion("1.0.0")
                .riskLevel(CapabilityRiskLevel.HIGH)
                .expiresAt(LocalDateTime.of(2026, 7, 24, 20, 0))
                .requestDigest("internal-digest")
                .build();
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, YudaoMcpAutoConfiguration.class, YudaoMcpAcfAutoConfiguration.class})
    static class TestApplication {

        @Bean
        CapabilityToolCatalog capabilityToolCatalog() {
            CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
            when(catalog.listDeclared()).thenReturn(ALL_TOOL_NAMES.stream()
                    .map(name -> descriptor(name, GATED_TOOL.equals(name)))
                    .toList());
            return catalog;
        }

        @Bean
        CapabilityToolInvoker capabilityToolInvoker() {
            CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
            when(invoker.invoke(any())).thenAnswer(invocation -> {
                CapabilityToolCall call = invocation.getArgument(0);
                if (GATED_TOOL.equals(call.getCapabilityName())) {
                    return CapabilityResult.confirmationRequired(GATED_TOOL, confirmationChallenge())
                            .withTraceId("trace-dual");
                }
                return CapabilityResult.success(call.getCapabilityName(), (Object) "hello")
                        .withTraceId("trace-dual");
            });
            return invoker;
        }

        @Bean
        CapabilityToolExportService capabilityToolExportService() {
            // P1-1：modern 腿的可见性走 export service，与 legacy 的 addToolFilter
            // 共用同一份 visibility 判定（visibilityService 的 getVisible 对
            // HIDDEN_TOOL 返回空）；同一用户下两腿的 tools/list 均为 16 个。
            // 此前 modern 侧没有这个 bean，仓库退回声明目录返回全量 17 个，那是
            // mock 配出来的假象，不是设计差异——生产上有 ACF 自动配置提供该 bean。
            CapabilityToolExportService exportService = mock(CapabilityToolExportService.class);
            when(exportService.export(any())).thenReturn(ALL_TOOL_NAMES.stream()
                    .filter(name -> !HIDDEN_TOOL.equals(name))
                    .map(name -> descriptor(name, GATED_TOOL.equals(name)))
                    .toList());
            return exportService;
        }

        @Bean
        CapabilityVisibilityService capabilityVisibilityService() {
            CapabilityVisibilityService service = mock(CapabilityVisibilityService.class);
            CapabilityDefinition visibleDefinition = mock(CapabilityDefinition.class);
            // 先声明通用规则，再声明特例：Mockito 按声明倒序匹配，特例后声明才生效。
            when(service.getVisible(anyString(), any())).thenReturn(Optional.of(visibleDefinition));
            when(service.getVisible(eq(HIDDEN_TOOL), any())).thenReturn(Optional.empty());
            return service;
        }

        @Bean
        McpTransportContextExtractor<HttpServletRequest> mcpTransportContextExtractor() {
            return request -> McpTransportContext.create(Map.of(
                    McpTransportContextKeys.USER_ID, 1L,
                    McpTransportContextKeys.TENANT_ID, 2L,
                    McpTransportContextKeys.CONSUMER_ID, "integration-test"));
        }

        @Bean
        WebProperties webProperties() {
            WebProperties properties = new WebProperties();
            properties.setAdminUi(new WebProperties.Ui());
            return properties;
        }

        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties();
        }

        @Bean
        OAuth2TokenCommonApi oauth2TokenCommonApi() {
            return mock(OAuth2TokenCommonApi.class);
        }

        @Bean("mcpSecurityFilterChain")
        SecurityFilterChain mcpSecurityFilterChain() {
            return mock(SecurityFilterChain.class);
        }
    }

}
