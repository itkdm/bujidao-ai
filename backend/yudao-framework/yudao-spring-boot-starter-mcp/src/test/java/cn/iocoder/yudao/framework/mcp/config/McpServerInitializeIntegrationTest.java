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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static cn.iocoder.yudao.framework.mcp.McpTestFixtures.modernHeaders;
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
                new HttpEntity<>(request, modernHeaders("server/discover")), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode payload = objectMapper.readTree(response.getBody());
        JsonNode result = payload.path("result");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(result.path("supportedVersions"))
                .isEqualTo(objectMapper.readTree("[\"2026-07-28\"]"));
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
    void shouldAcceptLegacyInitializeThroughRealServletContainer() throws Exception {
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

        // D3 起 /mcp 分流：无 modern 头的 legacy 握手走 legacy 腿，不再由 modern
        // 以 -32022 拒绝；legacy stateless 服务端对支持的版本直接回握手（SDK：
        // 不支持的版本也只回退到最高支持版，不报错）。完整 legacy 流程见
        // McpDualProtocolIntegrationTest，这里只锁定这个行为翻转点。
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("result").path("protocolVersion").asText())
                .isEqualTo("2025-11-25");
        assertThat(payload.path("result").path("serverInfo").path("name").asText())
                .isEqualTo("bujidao-mcp-server");
    }

    @Test
    void shouldDowngradeUnknownVersionInitializeToHighestSupportedOnLegacyLeg() throws Exception {
        // P1-5：legacy 握手从不拒版本：未知版本只记一条 warn，再按规范回它支持的
        // 最高版本 2025-11-25（HTTP 200）；而已支持的旧版（如 2025-06-18）
        // 原样回显。分流器注释曾误写为“legacy 对不支持的版本答 -32022”，已纠正。
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

        ResponseEntity<String> unknown = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2030-01-01\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"integration-test\",\"version\":\"1.0.0\"}}}",
                        headers),
                String.class);
        assertThat(unknown.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(unknown.getBody()).path("result")
                .path("protocolVersion").asText()).isEqualTo("2025-11-25");

        ResponseEntity<String> olderSupported = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"integration-test\",\"version\":\"1.0.0\"}}}",
                        headers),
                String.class);
        assertThat(olderSupported.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(olderSupported.getBody()).path("result")
                .path("protocolVersion").asText()).isEqualTo("2025-06-18");
    }

    @Test
    void shouldDowngradeModernVersionInitializeOnLegacyLeg() throws Exception {
        // 顶层 params.protocolVersion 不参与分流：带 2026-07-28 的 initialize
        // 无头、无 claim，走默认 legacy；legacy 服务端从不拒版本，按规范降级回
        // 它支持的最高版本 2025-11-25（HTTP 200）。缺 protocolVersion 同样降级。
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

        ResponseEntity<String> modernVersion = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2026-07-28\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"integration-test\",\"version\":\"1.0.0\"}}}",
                        headers),
                String.class);
        assertThat(modernVersion.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(modernVersion.getBody()).path("result")
                .path("protocolVersion").asText()).isEqualTo("2025-11-25");

        ResponseEntity<String> missingVersion = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":"
                        + "{\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"integration-test\",\"version\":\"1.0.0\"}}}",
                        headers),
                String.class);
        assertThat(missingVersion.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(objectMapper.readTree(missingVersion.getBody()).path("result")
                .path("protocolVersion").asText()).isEqualTo("2025-11-25");
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
                new HttpEntity<>(request, modernHeaders("initialize")), String.class);

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
                new HttpEntity<>(request, modernHeaders("tools/list")), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode tool = objectMapper.readTree(response.getBody()).path("result").path("tools").get(0);
        assertThat(tool.path("name").asText()).isEqualTo("demo.echo");
        assertThat(tool.path("title").asText()).isEqualTo("Echo");
        assertThat(tool.path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tool.path("outputSchema").path("properties").has("result")).isTrue();
        assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
    }

    @Test
    void shouldRejectNonEmptyCursorOnModernToolsList() throws Exception {
        // Cursor 语义差异的另一半：modern 仓库不支持分页，非空 cursor 以
        // -32602（HTTP 400）拒绝；legacy 忽略 cursor 返回全量（见
        // McpDualProtocolIntegrationTest）。
        String request = """
                {"jsonrpc":"2.0","id":6,"method":"tools/list","params":{"cursor":"page-2","_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, modernHeaders("tools/list")), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(payload.path("error").path("message").asText()).contains("Unknown cursor: page-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"resources/list", "resources/templates/list", "prompts/list"})
    void shouldRejectUnregisteredListMethodOnModernLeg(String method) throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":4,"method":"%s","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """.formatted(method);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, modernHeaders(method)), String.class);

        // 未注册对应 feature：不再返回空列表做兼容，直接 METHOD_NOT_FOUND。
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(payload.path("error").path("message").asText())
                .contains("Method not found: " + method);
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
                new HttpEntity<>(request, modernHeaders("tools/call", "demo.echo")), String.class);

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
