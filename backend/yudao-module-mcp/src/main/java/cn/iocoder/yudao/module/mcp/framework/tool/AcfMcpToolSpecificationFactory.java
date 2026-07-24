package cn.iocoder.yudao.module.mcp.framework.tool;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.module.mcp.framework.config.YudaoMcpToolProperties;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 根据显式白名单构造 MCP Tool 规格。
 *
 * @author bujidao
 */
public class AcfMcpToolSpecificationFactory {

    private final CapabilityToolCatalog capabilityToolCatalog;
    private final YudaoMcpToolProperties properties;
    private final AcfMcpToolCallHandler toolCallHandler;

    public AcfMcpToolSpecificationFactory(CapabilityToolCatalog capabilityToolCatalog,
                                          YudaoMcpToolProperties properties,
                                          AcfMcpToolCallHandler toolCallHandler) {
        this.capabilityToolCatalog = capabilityToolCatalog;
        this.properties = properties;
        this.toolCallHandler = toolCallHandler;
    }

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
                .destructiveHint(descriptor.isSideEffect())
                .idempotentHint(!descriptor.isSideEffect())
                .openWorldHint(false)
                .build();
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(descriptor.getCapabilityName())
                .title(descriptor.getTitle())
                .description(descriptor.getDescription())
                .inputSchema(McpSchemaAdapter.adaptInputSchema(descriptor.getInputSchema()))
                .outputSchema(McpSchemaAdapter.adaptOutputSchema(descriptor.getOutputSchema()))
                .annotations(annotations)
                .build();
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((transportContext, request) -> toolCallHandler.handle(transportContext, descriptor, request))
                .build();
    }

}
