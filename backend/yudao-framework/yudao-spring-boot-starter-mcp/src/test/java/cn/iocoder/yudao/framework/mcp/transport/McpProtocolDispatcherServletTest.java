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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class McpProtocolDispatcherServletTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ---- decide()：HTTP 方法 ----

    @Test
    void shouldRouteGetToLegacy() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("GET", null, null, null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("-");
    }

    @Test
    void shouldRouteDeleteToLegacy() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("DELETE", null, null, null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
    }

    @Test
    void shouldRouteUnknownHttpMethodToLegacyByDefault() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("PUT", null, null, null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("default-legacy");
    }

    // ---- decide()：头 ----

    @Test
    void shouldRoutePostWithMcpMethodHeaderToModern() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", "tools/list", null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.rule()).isEqualTo("mcp-method-header");
    }

    @Test
    void shouldRoutePostWithModernProtocolVersionHeaderToModern() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, "2026-07-28", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(decision.rule()).isEqualTo("protocol-version-2026-07-28");
    }

    @Test
    void shouldRoutePostWithLegacyProtocolVersionHeaderToLegacy() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, "2025-11-25", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(decision.rule()).isEqualTo("protocol-version-legacy");
    }

    @Test
    void shouldNotRouteToModernOnVersionHeaderPresenceAlone() {
        // 仅凭头存在不能分流：2025-06-18 同样要求该头，判的是值。
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, "2025-06-18", null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
    }

    // ---- decide()：体 ----

    @Test
    void shouldRoutePostWithModernMetaVersionToModern() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(decision.rule()).isEqualTo("meta-protocol-version-2026-07-28");
    }

    @Test
    void shouldRouteServerDiscoverToModern() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.MODERN);
        assertThat(decision.rule()).isEqualTo("server-discover");
    }

    @Test
    void shouldRouteInitializeWithoutModernSignalsToLegacy() throws Exception {
        // Legacy 握手：顶层 params.protocolVersion = 2025-11-25，无 _meta 版本。
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"workbuddy\",\"version\":\"1.0\"}}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(decision.rule()).isEqualTo("initialize");
    }

    @Test
    void shouldRoutePlainToolsListToLegacyByDefault() throws Exception {
        // 无头、无版本声明的 tools/list：老客户端兼容优先，默认 legacy。
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, null, body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("default-legacy");
    }

    @Test
    void shouldRouteUnparseableBodyToLegacyByDefault() {
        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, null, null);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("default-legacy");
    }

    @Test
    void shouldPreferHeaderVersionOverConflictingBody() throws Exception {
        // 头与体不一致时头说了算：头称 2025-11-25，体 _meta 称 2026-07-28，仍走 legacy。
        JsonNode body = OBJECT_MAPPER.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":"
                        + "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}");

        McpProtocolDispatcherServlet.Decision decision =
                McpProtocolDispatcherServlet.decide("POST", null, "2025-11-25", body);

        assertThat(decision.leg()).isEqualTo(McpProtocolDispatcherServlet.Leg.LEGACY);
        assertThat(decision.rule()).isEqualTo("protocol-version-legacy");
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
        MockHttpServletRequest source = new MockHttpServletRequest();
        source.setMethod("POST");
        source.setContentType("application/json");
        source.setContent("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"
                .getBytes(StandardCharsets.UTF_8));
        source.addHeader("Mcp-Method", "tools/list");
        HttpServletRequest request = new CacheRequestBodyWrapper(source);

        dispatcher.service(request, new MockHttpServletResponse());

        verify(modernTransport).service(any(ServletRequest.class), any(ServletResponse.class));
        verifyNoInteractions(legacyTransport);
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

    private static HttpServletRequest cachedJsonRequest(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return new CacheRequestBodyWrapper(request);
    }

}
