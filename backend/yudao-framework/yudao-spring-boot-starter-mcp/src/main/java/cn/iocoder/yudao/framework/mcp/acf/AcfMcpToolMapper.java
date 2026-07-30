package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * 将 ACF 协议无关工具描述映射为 MCP Tool。
 *
 * @author bujidao
 */
public class AcfMcpToolMapper {

    public McpSchema.Tool toTool(CapabilityToolDescriptor descriptor) {
        McpSchema.ToolAnnotations annotations = McpSchema.ToolAnnotations.builder()
                .title(descriptor.getTitle())
                .readOnlyHint(!descriptor.isSideEffect())
                .idempotentHint(!descriptor.isSideEffect())
                .build();
        return McpSchema.Tool.builder()
                .name(descriptor.getCapabilityName())
                .title(descriptor.getTitle())
                .description(descriptor.getDescription())
                .inputSchema(McpSchemaAdapter.adaptInputSchema(descriptor.getInputSchema()))
                .outputSchema(McpSchemaAdapter.adaptOutputSchema(descriptor.getOutputSchema()))
                .annotations(annotations)
                .meta(AcfMcpToolProtocolMetadata.toolMetadata(
                        descriptor.getVersion(),
                        descriptor.getRiskLevel() == null ? null : descriptor.getRiskLevel().name(),
                        descriptor.isIdempotencyRequired(),
                        descriptor.isConfirmationRequired()))
                .build();
    }

}
