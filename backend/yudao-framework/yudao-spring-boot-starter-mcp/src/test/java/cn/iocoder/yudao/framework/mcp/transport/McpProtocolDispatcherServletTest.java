package cn.iocoder.yudao.framework.mcp.transport;

import cn.iocoder.yudao.framework.web.core.filter.CacheRequestBodyWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.cachedJsonRequest;
import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.modernRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class McpProtocolDispatcherServletTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String META_PROTOCOL_VERSION_KEY = "io.modelcontextprotocol/protocolVersion";

    // ---- decide()：HTTP 方法 ----

    @ParameterizedTest
    @MethodSource("nonPostMethods")
    void shouldRouteNonPostMethodToLegacy(String httpMethod, String expectedRule) {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide(httpMethod, null, null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("-");
        assertThat(decision.rule()).isEqualTo(expectedRule);
    }

    static Stream<Arguments> nonPostMethods() {
        return Stream.of(
                Arguments.of("GET", "get-legacy"),
                Arguments.of("DELETE", "delete-legacy"),
                Arguments.of("PUT", "default-legacy"),
                Arguments.of("OPTIONS", "default-legacy"),
                Arguments.of("HEAD", "default-legacy"));
    }

    // ---- decide()：头 ----

    @Test
    void shouldRoutePostWithModernProtocolVersionHeaderToModern() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", "2026-07-28", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(decision.rule()).isEqualTo("protocol-version-2026-07-28");
    }

    @Test
    void shouldRoutePostWithLegacyProtocolVersionHeaderToLegacy() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", "2025-11-25", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(decision.rule()).isEqualTo("protocol-version-legacy");
    }

    @Test
    void shouldNotRouteToModernOnVersionHeaderPresenceAlone() {
        // 仅凭头存在不能分流：2025-06-18 同样要求该头，判的是值。
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", "2025-06-18", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("protocol-version-legacy");
    }

    @Test
    void shouldRoutePostWithPaddedModernVersionHeaderToLegacy() {
        // 路由与下游同为精确匹配：头值不 trim，带空格的 " 2026-07-28 " 不是 modern。
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", " 2026-07-28 ", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo(" 2026-07-28 ");
        assertThat(decision.rule()).isEqualTo("protocol-version-legacy");
    }

    // ---- decide()：体 claim ----

    @Test
    void shouldRoutePostWithModernMetaVersionClaimToModern() throws Exception {
        JsonNode body = claimBody("initialize", "2026-07-28");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(decision.rule()).isEqualTo("meta-protocol-version-claim");
    }

    @Test
    void shouldRoutePostWithAnyMetaVersionClaimToModern() throws Exception {
        // Claim 主规则只看保留键存在、不看值：任意版本（如 2025-06-18）同样进
        // modern，由 modern 腿答 -32022，不是在分流器里再判一次版本。
        JsonNode body = claimBody("tools/list", "2025-06-18");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.protocolVersion()).isEqualTo("2025-06-18");
        assertThat(decision.rule()).isEqualTo("meta-protocol-version-claim");
    }

    @Test
    void shouldRouteServerDiscoverWithoutClaimToLegacy() throws Exception {
        // 方法名不参与分类：无 claim 的 server/discover 与 modern 形态调用一样
        // 落默认 legacy，没有单独的 server-discover 分支。
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("-");
        assertThat(decision.rule()).isEqualTo("default-legacy");
    }

    @Test
    void shouldRouteInitializeWithoutClaimToLegacy() throws Exception {
        // 无 claim 的 initialize 落默认 legacy，没有单独的 initialize 分支；
        // 顶层 params.protocolVersion 不参与分类——2025-11-25 与 2026-07-28
        // 在这里没有区别（后者走 legacy 协商降级，见集成测试）。
        JsonNode legacyHandshake = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}");
        JsonNode modernVersionHandshake = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2026-07-28\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}");

        McpProtocolDispatcherServlet.Decision legacyDecision =
                McpProtocolDispatcherServlet.decide("POST", null, legacyHandshake);
        McpProtocolDispatcherServlet.Decision modernVersionDecision =
                McpProtocolDispatcherServlet.decide("POST", null, modernVersionHandshake);

        assertThat(legacyDecision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(legacyDecision.protocolVersion()).isEqualTo("-");
        assertThat(legacyDecision.rule()).isEqualTo("default-legacy");
        assertThat(modernVersionDecision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(modernVersionDecision.protocolVersion()).isEqualTo("-");
        assertThat(modernVersionDecision.rule()).isEqualTo("default-legacy");
    }

    @Test
    void shouldRoutePlainToolsListToLegacyByDefault() throws Exception {
        // 无头、无版本声明的 tools/list：老客户端兼容优先，默认 legacy。
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("default-legacy");
    }

    @Test
    void shouldRouteUnparseableBodyToLegacyByDefault() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("default-legacy");
    }

    // ---- decide()：头体冲突，头优先 ----

    @Test
    void shouldPreferHeaderVersionOverConflictingBodyClaim() throws Exception {
        // 头与体不一致时头说了算：头称 2025-11-25，体 _meta 称 2026-07-28，仍走 legacy。
        JsonNode body = claimBody("tools/list", "2026-07-28");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", "2025-11-25", body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(decision.rule()).isEqualTo("protocol-version-legacy");
    }

    @Test
    void shouldPreferModernHeaderOverConflictingBodyClaim() throws Exception {
        // 反向冲突同样头优先：头称 2026-07-28，体 _meta 称 2025-11-25，仍走
        // modern；进 modern 的冲突请求由 modern 腿的 SDK validateHeaders
        // 二次校验以 -32020 拒绝，不会静默执行。
        JsonNode body = claimBody("tools/list", "2025-11-25");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", "2026-07-28", body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(decision.rule()).isEqualTo("protocol-version-2026-07-28");
    }

    // ---- service()：转发 + 体可重读 ----

    @Test
    void shouldDispatchLegacyInitializeAndLeaveBodyReadable() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}";
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        byte[][] bodySeenByTransport = new byte[1][];
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            bodySeenByTransport[0] = request.getInputStream().readAllBytes();
            return null;
        }).when(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER);

        dispatcher.service(cachedJsonRequest(json), new MockHttpServletResponse());

        verify(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(modernTransport);
        // 分流器读过体之后，transport 还能读到完整 body。
        assertThat(bodySeenByTransport[0]).isEqualTo(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldDispatchModernRequestByHeader() throws Exception {
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER);
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

        dispatcher.service(modernRequest(json, "tools/list"), new MockHttpServletResponse());

        verify(modernTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(legacyTransport);
    }

    @Test
    void shouldNotTreatMcpMethodHeaderAsModernSignal() throws Exception {
        // Mcp-Method 头不参与分类：只带 Mcp-Method（甚至空串）、无版本头、无
        // claim 的请求按新规则落默认 legacy，不再是 modern 信号。
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER);
        MockHttpServletRequest source = new MockHttpServletRequest();
        source.setMethod("POST");
        source.setRequestURI("/mcp");
        source.setContentType("application/json");
        source.setContent("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
                .getBytes(StandardCharsets.UTF_8));
        source.addHeader("Mcp-Method", "");
        // 用 CacheRequestBodyWrapper 包一下，让分流器能读体（体里同样无 claim）。
        HttpServletRequest request = new CacheRequestBodyWrapper(source);

        dispatcher.service(request, new MockHttpServletResponse());

        verify(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(modernTransport);
    }

    @Test
    void shouldPreferHeaderOverClaimAtServiceLayer() throws Exception {
        // 头体冲突在 service 层同样头优先：头称 2025-11-25、体 claim 称
        // 2026-07-28，走 legacy。
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER);
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":"
                + "{\"_meta\":{\"" + META_PROTOCOL_VERSION_KEY + "\":\"2026-07-28\"}}}";
        MockHttpServletRequest source = new MockHttpServletRequest();
        source.setMethod("POST");
        source.setRequestURI("/mcp");
        source.setContentType("application/json");
        source.setContent(json.getBytes(StandardCharsets.UTF_8));
        source.addHeader("MCP-Protocol-Version", "2025-11-25");
        HttpServletRequest request = new CacheRequestBodyWrapper(source);

        dispatcher.service(request, new MockHttpServletResponse());

        verify(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(modernTransport);
    }

    @Test
    void shouldNotConsumeBodyWhenRequestIsNotCached() throws Exception {
        // 未包 CacheRequestBodyWrapper 的请求：分流器不读体，只按头判定；
        // transport 仍能读到完整 body。
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-11-25\"}}";
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        byte[][] bodySeenByTransport = new byte[1][];
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            bodySeenByTransport[0] = request.getInputStream().readAllBytes();
            return null;
        }).when(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));

        dispatcher.service(request, new MockHttpServletResponse());

        verify(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        assertThat(bodySeenByTransport[0]).isEqualTo(json.getBytes(StandardCharsets.UTF_8));
    }

    // ---- service()：legacy 开关关闭（只拦 POST） ----

    @Test
    void shouldRejectLegacyInitializeWhenLegacyDisabled() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}";
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.service(cachedJsonRequest(json), response);

        verifyNoInteractions(modernTransport);
        verifyNoInteractions(legacyTransport);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).contains("application/json");
        JsonNode payload = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertThat(payload.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(payload.path("id").asInt()).isEqualTo(1);
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(payload.path("error").path("message").asText()).contains("disabled");
        assertThat(payload.path("error").path("data").path("legacyEnabled").asBoolean()).isFalse();
        assertThat(payload.path("error").path("data").path("supportedProtocolVersion").asText())
                .isEqualTo("2026-07-28");
    }

    @Test
    void shouldRejectLegacyToolsListWhenLegacyDisabled() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\",\"params\":{}}";
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.service(cachedJsonRequest(json), response);

        verifyNoInteractions(modernTransport);
        verifyNoInteractions(legacyTransport);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).contains("application/json");
        JsonNode payload = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertThat(payload.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(payload.path("id").asInt()).isEqualTo(7);
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(payload.path("error").path("message").asText()).contains("disabled");
        assertThat(payload.path("error").path("data").path("legacyEnabled").asBoolean()).isFalse();
        assertThat(payload.path("error").path("data").path("supportedProtocolVersion").asText())
                .isEqualTo("2026-07-28");
    }

    @Test
    void shouldRejectEmptyBodyPostWithNullIdWhenLegacyDisabled() throws Exception {
        // 无 body 的 POST：分流器读不到体、按默认 legacy 判定，开关关闭时答
        // 400 + id: null 的错误信封（与带 id 的请求同一形态）。
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.service(cachedJsonRequest(""), response);

        verifyNoInteractions(modernTransport);
        verifyNoInteractions(legacyTransport);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).contains("application/json");
        JsonNode payload = OBJECT_MAPPER.readTree(response.getContentAsString());
        assertThat(payload.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(payload.path("id").isNull()).isTrue();
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(payload.path("error").path("message").asText()).contains("disabled");
        assertThat(payload.path("error").path("data").path("legacyEnabled").asBoolean()).isFalse();
        assertThat(payload.path("error").path("data").path("supportedProtocolVersion").asText())
                .isEqualTo("2026-07-28");
    }

    @ParameterizedTest
    @MethodSource("nonPostMethods")
    void shouldForwardNonPostMethodWhenLegacyDisabled(String httpMethod, String ignoredRule) {
        // Kill-switch 只对 POST 生效：GET / DELETE / 未知方法绕过开关，直接
        // 转发给 legacy transport 由它答 405，不再是 400 + legacy-disabled。
        // mock 的 legacy transport 不写响应，这里只断言转发本身；真实 405
        // 由 McpDualProtocolIntegrationTest 的 HTTP 用例锁定。
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(httpMethod);

        dispatcher.service(request, new MockHttpServletResponse());

        verify(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(modernTransport);
    }

    @Test
    void shouldAnswerLegacyNotificationWith202WhenLegacyDisabled() throws Exception {
        // 通知无 id、按 JSON-RPC 语义无响应通道：答 202 空体，不转发。
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.service(cachedJsonRequest(json), response);

        verifyNoInteractions(modernTransport);
        verifyNoInteractions(legacyTransport);
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void shouldStillRouteModernWhenLegacyDisabled() throws Exception {
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, false);
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{}}";

        dispatcher.service(modernRequest(json, "server/discover"), new MockHttpServletResponse());

        verify(modernTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(legacyTransport);
    }

    @Test
    void shouldRouteLegacyWhenLegacyExplicitlyEnabled() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";
        HttpServletMcpTransport modernTransport = mock(HttpServletMcpTransport.class);
        HttpServletStatelessServerTransport legacyTransport =
                mock(HttpServletStatelessServerTransport.class);
        McpProtocolDispatcherServlet dispatcher = new McpProtocolDispatcherServlet(
                modernTransport, legacyTransport, OBJECT_MAPPER, true);

        dispatcher.service(cachedJsonRequest(json), new MockHttpServletResponse());

        verify(legacyTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(modernTransport);
    }

    /** 带指定版本 claim 的请求体（params._meta 里放协议版本保留键）。 */
    private static JsonNode claimBody(String method, String claimVersion) throws Exception {
        return OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":"
                        + "{\"_meta\":{\"" + META_PROTOCOL_VERSION_KEY + "\":\""
                        + claimVersion + "\"}}}");
    }

}
