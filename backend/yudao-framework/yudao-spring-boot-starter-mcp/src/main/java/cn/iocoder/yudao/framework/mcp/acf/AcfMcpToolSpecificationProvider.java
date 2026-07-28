package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpAcfProperties;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import cn.iocoder.yudao.framework.mcp.tool.McpToolSpecificationProvider;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 根据显式白名单构造 ACF MCP Tool 规格。
 *
 * @author bujidao
 */
public class AcfMcpToolSpecificationProvider implements McpToolSpecificationProvider {

    private final CapabilityToolCatalog capabilityToolCatalog;
    private final YudaoMcpAcfProperties properties;
    private final AcfMcpToolCallHandler toolCallHandler;

    public AcfMcpToolSpecificationProvider(CapabilityToolCatalog capabilityToolCatalog,
                                           YudaoMcpAcfProperties properties,
                                           AcfMcpToolCallHandler toolCallHandler) {
        this.capabilityToolCatalog = capabilityToolCatalog;
        this.properties = properties;
        this.toolCallHandler = toolCallHandler;
    }

    @Override
    public List<McpStatelessServerFeatures.SyncToolSpecification> createToolSpecifications() {
        List<String> capabilityNames = properties.getExposedCapabilities();
        if (capabilityNames == null || capabilityNames.isEmpty()) {
            return List.of();
        }
        Set<String> uniqueNames = new HashSet<>();
        return capabilityNames.stream()
                .map(String::trim)
                .peek(name -> validateName(name, uniqueNames))
                .map(capabilityToolCatalog::getDeclared)
                .peek(this::validateExposure)
                .map(this::createToolSpecification)
                .toList();
    }

    private void validateName(String capabilityName, Set<String> uniqueNames) {
        if (capabilityName.isEmpty()) {
            throw new IllegalStateException("MCP exposed capability name must not be blank");
        }
        if (!uniqueNames.add(capabilityName)) {
            throw new IllegalStateException("Duplicate MCP exposed capability: " + capabilityName);
        }
    }

    private void validateExposure(CapabilityToolDescriptor descriptor) {
        if (descriptor.isSideEffect() && !properties.isAllowSideEffects()) {
            throw new IllegalStateException("MCP side-effect capability is not allowed: "
                    + descriptor.getCapabilityName());
        }
        if (descriptor.isConfirmationRequired() && !properties.isAllowConfirmationRequired()) {
            throw new IllegalStateException("MCP confirmation-required capability is not allowed: "
                    + descriptor.getCapabilityName());
        }
    }

    private McpStatelessServerFeatures.SyncToolSpecification createToolSpecification(
            CapabilityToolDescriptor descriptor) {
        McpSchema.ToolAnnotations annotations = McpSchema.ToolAnnotations.builder()
                .title(descriptor.getTitle())
                .readOnlyHint(!descriptor.isSideEffect())
                .idempotentHint(!descriptor.isSideEffect())
                .build();
        McpSchema.Tool tool = McpSchema.Tool.builder()
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
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((transportContext, request) -> toolCallHandler.handle(transportContext, descriptor, request))
                .build();
    }

}
