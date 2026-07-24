package cn.iocoder.yudao.module.mcp.framework.config;

import cn.iocoder.yudao.framework.acf.config.YudaoAcfAutoConfiguration;
import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.mcp.framework.security.McpTransportContextKeys;
import cn.iocoder.yudao.module.mcp.framework.tool.McpSchemaAdapter;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = McpAcfExecutionIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"yudao.mcp.server.enabled=true",
                "yudao.mcp.tools.exposed-capabilities=test.mcp.echo",
                "yudao.web.admin-ui.url=http://localhost"})
class McpAcfExecutionIntegrationTest {

    private static final Long USER_ID = 1001L;
    private static final String REQUIRED_PERMISSION = "mcp:test:echo";

    @LocalServerPort
    private int port;

    @Autowired
    private PermissionCommonApi permissionCommonApi;

    @Test
    void shouldInvokeRegisteredCapabilityThroughMcpAndAcfGovernance() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .openConnectionOnStartup(false)
                .resumableStreams(false)
                .build();
        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("acf-integration-test", "1.0.0"))
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(
                    McpSchema.CallToolRequest.builder("test.mcp.echo")
                            .arguments(Map.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY, "hello"))
                            .build());

            assertThat(result.isError()).isFalse();
            assertThat(result.structuredContent())
                    .isEqualTo(Map.of(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY, "acf:hello"));
            assertThat(result.content().get(0).toString()).contains("{\"result\":\"acf:hello\"}");
            verify(permissionCommonApi).hasAnyPermissions(USER_ID, REQUIRED_PERMISSION);
        }
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, ValidationAutoConfiguration.class,
            YudaoAcfAutoConfiguration.class, YudaoMcpServerAutoConfiguration.class})
    static class TestApplication {

        @Bean
        EchoCapability echoCapability() {
            return new EchoCapability();
        }

        @Bean
        PermissionCommonApi permissionCommonApi() {
            PermissionCommonApi permissionCommonApi = mock(PermissionCommonApi.class);
            when(permissionCommonApi.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
            return permissionCommonApi;
        }

        @Bean
        McpTransportContextExtractor<HttpServletRequest> mcpTransportContextExtractor() {
            return request -> McpTransportContext.create(Map.of(
                    McpTransportContextKeys.USER_ID, USER_ID,
                    McpTransportContextKeys.TENANT_ID, 2001L,
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

        @Bean("mcpSecurityFilterChain")
        SecurityFilterChain mcpSecurityFilterChain() {
            return mock(SecurityFilterChain.class);
        }
    }

    static class EchoCapability {

        @AgentCapability(name = "test.mcp.echo", title = "MCP ACF 回显",
                description = "验证 MCP 通过 ACF 治理与执行链调用能力", permissions = REQUIRED_PERMISSION)
        public String echo(String value) {
            return "acf:" + value;
        }
    }

}
