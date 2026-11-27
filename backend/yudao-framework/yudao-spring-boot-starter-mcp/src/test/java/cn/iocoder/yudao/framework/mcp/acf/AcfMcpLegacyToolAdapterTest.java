package cn.iocoder.yudao.framework.mcp.acf;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link AcfMcpLegacyToolAdapter} 单测：只验 modern -&gt; legacy 的字段级转写，
 * 不启动 Spring、不调 ACF。注意：本文件在本机未跑过（见提交说明），需在能跑
 * Maven 的环境重跑确认。
 */
class AcfMcpLegacyToolAdapterTest {

    @Test
    void shouldMapModernToolToLegacyToolFieldByField() {
        McpSchema.Tool modern = McpSchema.Tool.builder("demo.echo", Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")))
                .title("Demo Echo")
                .description("Echoes a message")
                .outputSchema(Map.of("type", "object"))
                .annotations(McpSchema.ToolAnnotations.builder()
                        .title("Demo Echo")
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .meta(Map.of("bujidao.ai/capabilityVersion", "1.0.0"))
                .icons(List.of(new McpSchema.Icon("https://example.com/icon.png", "image/png",
                        List.of("48x48"), "light")))
                .build();

        io.modelcontextprotocol.spec.McpSchema.Tool legacy =
                AcfMcpLegacyToolAdapter.toLegacyTool(modern);

        assertThat(legacy.name()).isEqualTo("demo.echo");
        assertThat(legacy.title()).isEqualTo("Demo Echo");
        assertThat(legacy.description()).isEqualTo("Echoes a message");
        assertThat(legacy.inputSchema()).isEqualTo(modern.inputSchema());
        assertThat(legacy.outputSchema()).isEqualTo(Map.of("type", "object"));
        assertThat(legacy.annotations().title()).isEqualTo("Demo Echo");
        assertThat(legacy.annotations().readOnlyHint()).isTrue();
        assertThat(legacy.annotations().destructiveHint()).isFalse();
        assertThat(legacy.annotations().idempotentHint()).isTrue();
        assertThat(legacy.annotations().openWorldHint()).isFalse();
        assertThat(legacy.meta()).containsEntry("bujidao.ai/capabilityVersion", "1.0.0");
        assertThat(legacy.icons()).hasSize(1);
        assertThat(legacy.icons().get(0).src()).isEqualTo("https://example.com/icon.png");
        assertThat(legacy.icons().get(0).mimeType()).isEqualTo("image/png");
        assertThat(legacy.icons().get(0).sizes()).containsExactly("48x48");
        assertThat(legacy.icons().get(0).theme()).isEqualTo("light");
    }

    @Test
    void shouldMapModernCallToolResultToLegacyResult() {
        McpSchema.CallToolResult modern = McpSchema.CallToolResult.builder()
                .addContent(McpSchema.TextContent.builder("{\"message\":\"hello\"}").build())
                .structuredContent(Map.of("message", "hello"))
                .meta(Map.of(AcfMcpToolProtocolMetadata.STATUS, "SUCCESS"))
                .isError(false)
                .build();

        io.modelcontextprotocol.spec.McpSchema.CallToolResult legacy =
                AcfMcpLegacyToolAdapter.toLegacyResult(modern, jsonMapper());

        assertThat(legacy.isError()).isFalse();
        assertThat(legacy.content()).hasSize(1);
        assertThat(legacy.content().get(0))
                .isInstanceOf(io.modelcontextprotocol.spec.McpSchema.TextContent.class);
        assertThat(((io.modelcontextprotocol.spec.McpSchema.TextContent) legacy.content().get(0)).text())
                .isEqualTo("{\"message\":\"hello\"}");
        assertThat(legacy.structuredContent()).isEqualTo(Map.of("message", "hello"));
        assertThat(legacy.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "SUCCESS");
    }

    @Test
    void shouldPreserveErrorFlagOnLegacyResult() {
        McpSchema.CallToolResult modern = McpSchema.CallToolResult.builder()
                .addContent(McpSchema.TextContent.builder("Permission denied").build())
                .meta(Map.of(AcfMcpToolProtocolMetadata.STATUS, "DENIED",
                        AcfMcpToolProtocolMetadata.ERROR_CODE, "PERMISSION_DENIED"))
                .isError(true)
                .build();

        io.modelcontextprotocol.spec.McpSchema.CallToolResult legacy =
                AcfMcpLegacyToolAdapter.toLegacyResult(modern, jsonMapper());

        assertThat(legacy.isError()).isTrue();
        assertThat(legacy.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE, "PERMISSION_DENIED");
        assertThat(legacy.structuredContent()).isNull();
    }

    @Test
    void shouldConvertInputRequiredResultToLegacyErrorWithoutExecuting() {
        McpSchema.InputRequiredResult modern = McpSchema.InputRequiredResult.builder()
                .elicit(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY,
                        McpSchema.ElicitFormRequest.builder(
                                "Capability 'demo.echo' still requires your confirmation before it runs.",
                                Map.of("type", "object", "properties", Map.of())).build())
                .requestState("challenge-001")
                .meta(Map.of(AcfMcpToolProtocolMetadata.STATUS, "CONFIRM_REQUIRED",
                        AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE,
                        Map.of("challengeId", "challenge-001", "capabilityName", "demo.echo")))
                .build();

        io.modelcontextprotocol.spec.McpSchema.CallToolResult legacy =
                AcfMcpLegacyToolAdapter.toLegacyResult(modern, jsonMapper());

        assertThat(legacy.isError()).isTrue();
        assertThat(legacy.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "CONFIRM_REQUIRED");
        assertThat(legacy.meta()).containsKey(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE);
        assertThat(legacy.content()).hasSize(1);
        assertThat(legacy.content().get(0))
                .isInstanceOf(io.modelcontextprotocol.spec.McpSchema.TextContent.class);
        assertThat(((io.modelcontextprotocol.spec.McpSchema.TextContent) legacy.content().get(0))
                .text()).contains("cannot elicit confirmation");
    }

    @Test
    void shouldMapMinimalModernToolToLegacyToolWithNullOptionals() {
        // 只给必填字段：outputSchema / annotations / meta / icons 全是 null，
        // 守卫分支必须直接跳过、不抛异常，legacy 侧对应字段同样为 null。
        McpSchema.Tool modern = McpSchema.Tool.builder("demo.minimal",
                Map.of("type", "object")).build();

        io.modelcontextprotocol.spec.McpSchema.Tool legacy =
                AcfMcpLegacyToolAdapter.toLegacyTool(modern);

        assertThat(legacy.name()).isEqualTo("demo.minimal");
        assertThat(legacy.inputSchema()).isEqualTo(Map.of("type", "object"));
        assertThat(legacy.title()).isNull();
        assertThat(legacy.description()).isNull();
        assertThat(legacy.outputSchema()).isNull();
        assertThat(legacy.annotations()).isNull();
        assertThat(legacy.meta()).isNull();
        assertThat(legacy.icons()).isNull();
    }

    @Test
    void shouldRejectUnknownModernCallToolOutcome() {
        // CallToolOutcome 是普通接口（非密封），mock 一个既非 CallToolResult
        // 又非 InputRequiredResult 的实现，必须以 IllegalArgumentException 拒绝。
        McpSchema.CallToolOutcome unknown = mock(McpSchema.CallToolOutcome.class);

        assertThatThrownBy(() -> AcfMcpLegacyToolAdapter.toLegacyResult(unknown, jsonMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported modern CallToolOutcome");
    }

    @Test
    void shouldPassThroughTextContentAnnotationsAndMeta() {
        McpSchema.CallToolResult modern = McpSchema.CallToolResult.builder()
                .addContent(McpSchema.TextContent.builder("hello")
                        .annotations(McpSchema.Annotations.builder()
                                .audience(List.of(McpSchema.Role.USER))
                                .priority(0.5)
                                .lastModified("2026-07-24T20:00:00Z")
                                .build())
                        .meta(Map.of("trace", "trace-001"))
                        .build())
                .isError(false)
                .build();

        io.modelcontextprotocol.spec.McpSchema.CallToolResult legacy =
                AcfMcpLegacyToolAdapter.toLegacyResult(modern, jsonMapper());

        assertThat(legacy.content()).hasSize(1);
        assertThat(legacy.content().get(0))
                .isInstanceOf(io.modelcontextprotocol.spec.McpSchema.TextContent.class);
        io.modelcontextprotocol.spec.McpSchema.TextContent text =
                (io.modelcontextprotocol.spec.McpSchema.TextContent) legacy.content().get(0);
        assertThat(text.text()).isEqualTo("hello");
        assertThat(text.annotations().audience())
                .containsExactly(io.modelcontextprotocol.spec.McpSchema.Role.USER);
        assertThat(text.annotations().priority()).isEqualTo(0.5);
        assertThat(text.annotations().lastModified()).isEqualTo("2026-07-24T20:00:00Z");
        assertThat(text.meta()).containsEntry("trace", "trace-001");
    }

    @Test
    void shouldSerializeNonTextContentToJsonTextForLegacyResult() {
        McpSchema.CallToolResult modern = McpSchema.CallToolResult.builder()
                .addContent(new McpSchema.ImageContent(null, "aGVsbG8=", "image/png", null))
                .isError(false)
                .build();

        io.modelcontextprotocol.spec.McpSchema.CallToolResult legacy =
                AcfMcpLegacyToolAdapter.toLegacyResult(modern, jsonMapper());

        assertThat(legacy.isError()).isFalse();
        assertThat(legacy.content()).hasSize(1);
        assertThat(legacy.content().get(0))
                .isInstanceOf(io.modelcontextprotocol.spec.McpSchema.TextContent.class);
        assertThat(((io.modelcontextprotocol.spec.McpSchema.TextContent) legacy.content().get(0)).text())
                .contains("image/png");
    }

    private static JacksonMcpJsonMapper jsonMapper() {
        return new JacksonMcpJsonMapper(new ObjectMapper());
    }

}
