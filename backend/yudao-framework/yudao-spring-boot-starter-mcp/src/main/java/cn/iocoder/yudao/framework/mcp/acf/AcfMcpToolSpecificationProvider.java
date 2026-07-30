package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.mcp.tool.McpToolSpecificationProvider;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import java.util.List;

/**
 * 根据代码中声明的 ACF 能力构造 MCP Tool 规格。
 *
 * <p>该静态规格只用于 SDK 的 tools/call 路由。tools/list 由
 * {@link AcfMcpToolsListFilter} 按当前用户权限动态返回，避免将无权限工具暴露给客户端。</p>
 *
 * @author bujidao
 */
public class AcfMcpToolSpecificationProvider implements McpToolSpecificationProvider {

    private final CapabilityToolCatalog capabilityToolCatalog;
    private final AcfMcpToolMapper toolMapper;
    private final AcfMcpToolCallHandler toolCallHandler;

    public AcfMcpToolSpecificationProvider(CapabilityToolCatalog capabilityToolCatalog,
                                           AcfMcpToolMapper toolMapper,
                                           AcfMcpToolCallHandler toolCallHandler) {
        this.capabilityToolCatalog = capabilityToolCatalog;
        this.toolMapper = toolMapper;
        this.toolCallHandler = toolCallHandler;
    }

    @Override
    public List<McpStatelessServerFeatures.SyncToolSpecification> createToolSpecifications() {
        return capabilityToolCatalog.listDeclared().stream()
                .map(this::createToolSpecification)
                .toList();
    }

    private McpStatelessServerFeatures.SyncToolSpecification createToolSpecification(
            CapabilityToolDescriptor descriptor) {
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(toolMapper.toTool(descriptor))
                .callHandler((transportContext, request) -> toolCallHandler.handle(transportContext, descriptor, request))
                .build();
    }

}
