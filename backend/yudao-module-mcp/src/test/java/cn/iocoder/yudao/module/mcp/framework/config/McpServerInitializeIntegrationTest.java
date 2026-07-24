package cn.iocoder.yudao.module.mcp.framework.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.mcp.framework.security.McpTransportContextKeys;
import cn.iocoder.yudao.module.mcp.framework.tool.McpToolProtocolMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = McpServerInitializeIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"yudao.mcp.server.enabled=true",
                "yudao.mcp.tools.exposed-capabilities=demo.echo",
                "yudao.web.admin-ui.url=http://localhost"})
class McpServerInitializeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldInitializeThroughRealServletContainer() throws Exception {
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

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("result").path("protocolVersion").asText()).isEqualTo("2025-11-25");
        assertThat(payload.path("result").path("serverInfo").path("name").asText())
                .isEqualTo("bujidao-mcp-server");
        assertThat(payload.path("result").path("capabilities").isObject()).isTrue();
        assertThat(payload.path("result").path("capabilities").path("tools").isObject()).isTrue();
    }

    @Test
    void shouldListAllowlistedAcfTools() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("MCP-Protocol-Version", "2025-11-25");
        String request = """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode tool = objectMapper.readTree(response.getBody()).path("result").path("tools").get(0);
        assertThat(tool.path("name").asText()).isEqualTo("demo.echo");
        assertThat(tool.path("title").asText()).isEqualTo("Echo");
        assertThat(tool.path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tool.path("outputSchema").path("properties").has("result")).isTrue();
        assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
    }

    @Test
    void shouldCallAllowlistedToolThroughAcfInvoker() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("MCP-Protocol-Version", "2025-11-25");
        String request = """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"demo.echo","arguments":{"message":"hello"}
                }}
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/mcp",
                new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode result = objectMapper.readTree(response.getBody()).path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("structuredContent").path("result").asText()).isEqualTo("hello");
        assertThat(result.path("_meta").path(McpToolProtocolMetadata.TRACE_ID).asText())
                .isEqualTo("trace-integration");
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, YudaoMcpServerAutoConfiguration.class})
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
            when(catalog.getDeclared("demo.echo")).thenReturn(descriptor);
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
    }

}
