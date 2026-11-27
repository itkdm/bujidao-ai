package cn.iocoder.yudao.framework.mcp.acf;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Modern（MCP 2026-07-28）与 legacy（MCP 2025-11-25）两种 Tool / 结果类型之间的薄转接层。
 *
 * <p>核实依据（MCP Java SDK v2.2.0 tag 源码，commit {@code 81aed21}）：</p>
 * <ul>
 * <li>{@code io.modelcontextprotocol.server.McpStatelessServerFeatures} 存在，
 * {@code SyncToolSpecification} 是 {@code (McpSchema.Tool tool, BiFunction<McpTransportContext,
 * CallToolRequest, McpSchema.CallToolResult> callHandler)}，其中 {@code CallToolRequest} /
 * {@code CallToolResult} 都是 {@code io.modelcontextprotocol.spec.McpSchema} 里的 legacy 类型，
 * 第一个参数是 {@code McpTransportContext}，不是 {@code McpSyncServerExchange}。</li>
 * <li>legacy {@code McpSchema.Tool} 的非废弃写法是
 * {@code Tool.builder(String name, Map<String, Object> inputSchema)}，链式
 * {@code .name() / .inputSchema()} 仍存在但已标记 {@code @Deprecated}。</li>
 * <li>modern 与 legacy 的 {@code Tool} / {@code ToolAnnotations} / {@code TextContent} /
 * {@code Annotations} / {@code Icon} 字段一一对应（legacy 的 {@code ToolAnnotations} 只多一个
 * {@code returnDirect}，modern 没有对应字段，转过去留空）。</li>
 * </ul>
 *
 * <p>同源保证：本类不持有任何工具定义，只做 modern {@code McpSchema.Tool}（由
 * {@link AcfMcpToolMapper} 从同一份 {@code CapabilityToolDescriptor} 产出）到 legacy
 * {@code McpSchema.Tool} 的字段级转写。两边工具名、描述、inputSchema、outputSchema、
 * annotations、_meta 来自同一份 descriptor，不存在第二份清单。</p>
 *
 * <p>结果转写：modern {@code CallToolResult} 的 content / structuredContent / isError /
 * _meta 原样转过去，{@code resultType} 是 modern 才有的字段，legacy 没有，直接丢弃。
 * modern 的 {@code InputRequiredResult} 在 legacy 协议里没有对应形态（2025-11-25 没有
 * MRTR 的 requestState / inputResponses 往返，stateless 服务端的 callHandler 也拿不到
 * 能发 elicitation 的 exchange），转成一条明确的错误结果：{@code isError=true}，
 * _meta 原样保留（STATUS=CONFIRM_REQUIRED 与 challenge 摘要都在里面），正文说明 legacy
 * 腿无法完成确认、不要静默执行。注意：这条分支只是防御性的——{@link AcfMcpToolCallHandler}
 * 在 legacy 入口固定传“不支持 elicitation 表单”，需要确认的能力在 handler 内就已经
 * 以 CONFIRMATION_UNAVAILABLE 错误结果返回，不会走到这里。</p>
 *
 * <p>确认能力不在这里复活：本类不产生 {@code acf.confirm} 工具，legacy 腿需要确认的
 * 能力怎么处理留给后续块决定，本块只保证不把它加进规格列表。</p>
 *
 * @author bujidao
 */
public final class AcfMcpLegacyToolAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcfMcpLegacyToolAdapter.class);

    private AcfMcpLegacyToolAdapter() {
    }

    /**
     * 把 modern Tool 转成 legacy Tool：字段一一对应，inputSchema / outputSchema / _meta
     * 共享同一份 Map 引用（mapper 产出后不再改动，只读使用）。
     */
    public static io.modelcontextprotocol.spec.McpSchema.Tool toLegacyTool(McpSchema.Tool tool) {
        io.modelcontextprotocol.spec.McpSchema.Tool.Builder builder =
                io.modelcontextprotocol.spec.McpSchema.Tool.builder(tool.name(), tool.inputSchema())
                        .title(tool.title())
                        .description(tool.description());
        if (tool.outputSchema() != null) {
            builder.outputSchema(tool.outputSchema());
        }
        if (tool.annotations() != null) {
            builder.annotations(toLegacyToolAnnotations(tool.annotations()));
        }
        if (tool.meta() != null) {
            builder.meta(tool.meta());
        }
        if (tool.icons() != null) {
            builder.icons(tool.icons().stream()
                    .map(AcfMcpLegacyToolAdapter::toLegacyIcon)
                    .toList());
        }
        return builder.build();
    }

    /**
     * 把 modern tools/call 结果转成 legacy {@code CallToolResult}。
     *
     * @param outcome modern handler 的返回值（{@code CallToolResult} 或
     *                {@code InputRequiredResult}）
     * @param jsonMapper 只在 modern 结果里出现非文本 content 时用它序列化成文本；
     *                   handler 只产文本 content，正常走不到这条路
     */
    public static io.modelcontextprotocol.spec.McpSchema.CallToolResult toLegacyResult(
            McpSchema.CallToolOutcome outcome, McpJsonMapper jsonMapper) {
        if (outcome instanceof McpSchema.InputRequiredResult required) {
            return toLegacyConfirmationRequired(required);
        }
        if (!(outcome instanceof McpSchema.CallToolResult result)) {
            throw new IllegalArgumentException("Unsupported modern CallToolOutcome: "
                    + outcome.getClass().getName());
        }
        io.modelcontextprotocol.spec.McpSchema.CallToolResult.Builder builder =
                io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                        .content(toLegacyContent(result.content(), jsonMapper))
                        .isError(Boolean.TRUE.equals(result.isError()));
        if (result.structuredContent() != null) {
            builder.structuredContent(result.structuredContent());
        }
        if (result.meta() != null) {
            builder.meta(result.meta());
        }
        return builder.build();
    }

    /**
     * MRTR 的 InputRequiredResult 在 legacy 协议里没有对应形态：转成错误结果，不执行。
     * _meta 原样保留，客户端与日志仍能看到 CONFIRM_REQUIRED 状态与 challenge 摘要。
     */
    private static io.modelcontextprotocol.spec.McpSchema.CallToolResult toLegacyConfirmationRequired(
            McpSchema.InputRequiredResult required) {
        String text = "Capability requires confirmation before it runs, but the legacy MCP "
                + "protocol leg cannot elicit confirmation. Use the modern MCP endpoint "
                + "(2026-07-28) to confirm and retry.";
        io.modelcontextprotocol.spec.McpSchema.CallToolResult.Builder builder =
                io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                        .addTextContent(text)
                        .isError(true);
        if (required.meta() != null) {
            builder.meta(required.meta());
        }
        return builder.build();
    }

    private static List<io.modelcontextprotocol.spec.McpSchema.Content> toLegacyContent(
            List<McpSchema.Content> content, McpJsonMapper jsonMapper) {
        if (content == null) {
            return List.of();
        }
        return content.stream()
                .map(item -> toLegacyContentItem(item, jsonMapper))
                .toList();
    }

    private static io.modelcontextprotocol.spec.McpSchema.Content toLegacyContentItem(
            McpSchema.Content item, McpJsonMapper jsonMapper) {
        if (item instanceof McpSchema.TextContent textContent) {
            io.modelcontextprotocol.spec.McpSchema.TextContent.Builder builder =
                    io.modelcontextprotocol.spec.McpSchema.TextContent.builder(textContent.text());
            if (textContent.annotations() != null) {
                builder.annotations(toLegacyAnnotations(textContent.annotations()));
            }
            if (textContent.meta() != null) {
                builder.meta(textContent.meta());
            }
            return builder.build();
        }
        // handler 只产文本 content；其它类型（图片等）没有 legacy 对应物时退化成 JSON 文本，
        // 不丢信息也不让转写炸掉整条调用。
        try {
            String serialized = jsonMapper.writeValueAsString(item);
            LOGGER.warn("Non-text MCP content converted to JSON text for legacy result: contentType={}",
                    item.getClass().getName());
            return io.modelcontextprotocol.spec.McpSchema.TextContent.builder(serialized).build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize MCP content for legacy result", exception);
        }
    }

    private static io.modelcontextprotocol.spec.McpSchema.ToolAnnotations toLegacyToolAnnotations(
            McpSchema.ToolAnnotations annotations) {
        return io.modelcontextprotocol.spec.McpSchema.ToolAnnotations.builder()
                .title(annotations.title())
                .readOnlyHint(annotations.readOnlyHint())
                .destructiveHint(annotations.destructiveHint())
                .idempotentHint(annotations.idempotentHint())
                .openWorldHint(annotations.openWorldHint())
                .build();
    }

    private static io.modelcontextprotocol.spec.McpSchema.Annotations toLegacyAnnotations(
            McpSchema.Annotations annotations) {
        List<io.modelcontextprotocol.spec.McpSchema.Role> audience = annotations.audience() == null ? null
                : annotations.audience().stream()
                        .map(role -> io.modelcontextprotocol.spec.McpSchema.Role.valueOf(role.name()))
                        .toList();
        return io.modelcontextprotocol.spec.McpSchema.Annotations.builder()
                .audience(audience)
                .priority(annotations.priority())
                .lastModified(annotations.lastModified())
                .build();
    }

    private static io.modelcontextprotocol.spec.McpSchema.Icon toLegacyIcon(McpSchema.Icon icon) {
        return new io.modelcontextprotocol.spec.McpSchema.Icon(icon.src(), icon.mimeType(),
                icon.sizes(), icon.theme());
    }

}
