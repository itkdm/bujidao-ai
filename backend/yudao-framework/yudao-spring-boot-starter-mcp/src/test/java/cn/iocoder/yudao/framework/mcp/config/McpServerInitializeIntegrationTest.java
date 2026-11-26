package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolProtocolMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Modern 协议集成测试：没有 initialize 握手，客户端每次请求在 {@code _meta} 里带
 * 协议版本与能力声明；发现走 {@code server/discover}。
 *
 * <p>原始 JSON 请求必须带齐 transport 校验头：{@code Mcp-Method}、
 * {@code MCP-Protocol-Version}（与 {@code _meta} 里的版本一致），
 * {@code tools/call} 还要带 {@code Mcp-Name}。</p>
 */
@SpringBootTest(classes = McpServerInitializeIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"yudao.mcp.server.enabled=true",
                "yudao.web.admin-ui.url=http://localhost"})
class McpServerInitializeIntegrationTest {

    private static final String PROTOCOL_VERSION = "2026-07-28";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDiscoverServerThroughRealServletContainer() throws Exception {
        String request = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "server/discover",
                  "params": {
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                      "io.modelcontextprotocol/clientCapabilities": {}
                    }
                  }
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, mcpHeaders("server/discover")), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode payload = objectMapper.readTree(response.getBody());
        JsonNode result = payload.path("result");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(result.path("supportedVersions").toString()).isEqualTo("[\"2026-07-28\"]");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
        // 未注册 Resources / Prompts feature：discover 不宣告，对应方法返回 METHOD_NOT_FOUND。
        assertThat(result.path("capabilities").path("resources").isMissingNode()).isTrue();
        assertThat(result.path("capabilities").path("prompts").isMissingNode()).isTrue();
        assertThat(result.path("ttlMs").asLong()).isEqualTo(0L);
        assertThat(result.path("cacheScope").asText()).isEqualTo("private");
        assertThat(result.path("_meta").path("io.modelcontextprotocol/serverInfo")
                .path("name").asText()).isEqualTo("bujidao-mcp-server");
    }

    @Test
    void shouldRejectLegacyInitializeThroughRealServletContainer() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        String request = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {},
                    "clientInfo": {"name": "integration-test", "version": "1.0.0"}
                  }
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, headers), String.class);

        // 旧版握手没有 _meta 版本声明：服务端以 -32022 拒绝，并告知支持的版本。
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(payload.path("error").path("message").asText())
                .contains("does not support the initialize handshake");
        assertThat(payload.path("error").path("data").path("supported").toString())
                .isEqualTo("[\"2026-07-28\"]");
        assertThat(payload.path("error").path("data").path("requested").asText())
                .isEqualTo("2025-11-25");
    }

    @Test
    void shouldRejectInitializeWithModernVersionClaimAsUnknownMethod() throws Exception {
        String request = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "_meta": {
                      "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                      "io.modelcontextprotocol/clientCapabilities": {}
                    }
                  }
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, mcpHeaders("initialize")), String.class);

        // 带 modern 版本声明的 initialize 不再走 legacy 拒绝分支：服务端根本没有
        // 这个方法，以 METHOD_NOT_FOUND 回答。
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(payload.path("error").path("message").asText())
                .contains("Method not found: initialize");
    }

    @Test
    void shouldRejectInvalidOriginThroughRealServletContainer() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setOrigin("https://attacker.example");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>("{}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void shouldRejectQueryTokenThroughRealServletContainer() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp?token=sensitive-token",
                new HttpEntity<>("{}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getBody()).doesNotContain("sensitive-token");
    }

    @Test
    void shouldListDeclaredAcfTools() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, mcpHeaders("tools/list")), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode tool = objectMapper.readTree(response.getBody()).path("result").path("tools").get(0);
        assertThat(tool.path("name").asText()).isEqualTo("demo.echo");
        assertThat(tool.path("title").asText()).isEqualTo("Echo");
        assertThat(tool.path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tool.path("outputSchema").path("properties").has("result")).isTrue();
        assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
    }

    @Test
    void shouldRejectResourcesListWhenNoResourcesFeature() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":4,"method":"resources/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, mcpHeaders("resources/list")), String.class);

        // 未注册 Resources feature：不再返回空列表做兼容，直接 METHOD_NOT_FOUND。
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(payload.path("error").path("message").asText())
                .contains("Method not found: resources/list");
    }

    @Test
    void shouldRejectResourceTemplatesListWhenNoResourcesFeature() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":5,"method":"resources/templates/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, mcpHeaders("resources/templates/list")), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(payload.path("error").path("message").asText())
                .contains("Method not found: resources/templates/list");
    }

    @Test
    void shouldCallAllowlistedToolThroughAcfInvoker() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"demo.echo","arguments":{"message":"hello"},
                  "_meta":{
                    "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                    "io.modelcontextprotocol/clientCapabilities":{}}
                }}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, mcpHeaders("tools/call", "demo.echo")), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode result = objectMapper.readTree(response.getBody()).path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("structuredContent").path("result").asText()).isEqualTo("hello");
        assertThat(result.path("_meta").path(AcfMcpToolProtocolMetadata.TRACE_ID).asText())
                .isEqualTo("trace-integration");
    }

    @Test
    void shouldWorkWithOfficialMcpClient() {
        HttpClientMcpTransport transport = HttpClientMcpTransport
                .builder("http://127.0.0.1:" + port + "/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("official-client-test", "1.0.0").build())
                .roundTimeout(Duration.ofSeconds(10))
                .build();

        // Modern 客户端没有 initialize：先 discover，再直接调工具。
        McpSchema.DiscoverResult discoverResult = client.discover();
        assertThat(discoverResult.supportedVersions()).containsExactly("2026-07-28");
        assertThat(discoverResult.capabilities().tools()).isNotNull();
        assertThat(discoverResult.capabilities().resources()).isNull();
        assertThat(discoverResult.capabilities().prompts()).isNull();
        Map<?, ?> serverInfo = (Map<?, ?>) discoverResult.meta()
                .get("io.modelcontextprotocol/serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo("bujidao-mcp-server");

        McpSchema.ListToolsResult toolsResult = client.listTools().list();
        assertThat(toolsResult.tools()).extracting(McpSchema.Tool::name)
                .containsExactly("demo.echo");

        McpSchema.CallToolResult callResult = client.tool("demo.echo")
                .arguments(Map.of("message", "hello"))
                .call();
        assertThat(callResult.isError()).isFalse();
        assertThat(callResult.structuredContent())
                .isEqualTo(Map.of(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY, "hello"));
        assertThat(callResult.meta()).containsEntry(AcfMcpToolProtocolMetadata.TRACE_ID, "trace-integration");
    }

    private static HttpHeaders mcpHeaders(String method) {
        return mcpHeaders(method, null);
    }

    private static HttpHeaders mcpHeaders(String method, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("Mcp-Method", method);
        headers.set("MCP-Protocol-Version", PROTOCOL_VERSION);
        if (name != null) {
            headers.set("Mcp-Name", name);
        }
        return headers;
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, YudaoMcpAutoConfiguration.class, YudaoMcpAcfAutoConfiguration.class})
    static class TestApplication {

        @Bean
        CapabilityToolCatalog capabilityToolCatalog() {
            CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
            CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
            when(descriptor.getCapabilityName()).thenReturn("demo.echo");
            when(descriptor.getTitle()).thenReturn("Echo");
            when(descriptor.getDescription()).thenReturn("Echo input");
            when(descriptor.getInputSchema()).thenReturn(Map.of("type", "object", "properties",
                    Map.of("message", Map.of("type", "string"))));
            when(descriptor.getOutputSchema()).thenReturn(Map.of("type", "string"));
            when(catalog.listDeclared()).thenReturn(java.util.List.of(descriptor));
            return catalog;
        }

        @Bean
        CapabilityToolInvoker capabilityToolInvoker() {
            CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
            when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(CapabilityResult.success("demo.echo", (Object) "hello")
                            .withTraceId("trace-integration"));
            return invoker;
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
