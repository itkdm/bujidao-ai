package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import io.modelcontextprotocol.modern.McpSchema;

import java.util.Map;

/**
 * 将 ACF 协议无关工具描述映射为 MCP Tool。
 *
 * @author bujidao
 */
public class AcfMcpToolMapper {

    private final CapabilityToolContractSupport toolContractSupport;

    public AcfMcpToolMapper() {
        this(new CapabilityToolContractSupport());
    }

    public AcfMcpToolMapper(CapabilityToolContractSupport toolContractSupport) {
        this.toolContractSupport = toolContractSupport;
    }

    public McpSchema.Tool toTool(CapabilityToolDescriptor descriptor) {
        McpSchema.ToolAnnotations annotations = McpSchema.ToolAnnotations.builder()
                .title(descriptor.getTitle())
                .readOnlyHint(!descriptor.isSideEffect())
                .idempotentHint(!descriptor.isSideEffect())
                .build();
        Map<String, Object> inputSchema = toolContractSupport.enrichInputSchema(
                McpSchemaAdapter.adaptInputSchema(descriptor.getInputSchema()),
                descriptor.isIdempotencyRequired());
        // Modern Tool.builder(name, inputSchema)：name 与 inputSchema 是必填构造参数，
        // 不再有 .name() / .inputSchema() 方法。
        return McpSchema.Tool.builder(descriptor.getCapabilityName(), inputSchema)
                .title(descriptor.getTitle())
                .description(descriptor.getDescription())
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
