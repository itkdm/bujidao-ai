package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 根据代码中声明的 ACF 能力构造 MCP Tool 目录（modern repository 模式）。
 *
 * <p>list：按当前调用身份返回可见工具。可见性由 {@link CapabilityToolExportService}
 * 按 transport context 里的身份判定，顺序按能力名称升序（与导出顺序无关），
 * 确定性；不支持分页，任何非空 cursor 都按 SDK 约定以 invalid params 拒绝。</p>
 *
 * <p>find：只按名称查找，不做可见性过滤——无权限的调用仍要进 ACF 执行链，
 * 由治理层返回权限错误，而不是在这里伪装成“工具不存在”。未知名称返回 null，
 * SDK 会答复 Unknown tool。</p>
 *
 * <p>确认不走独立工具：需要确认的能力由 {@link AcfMcpToolCallHandler} 在工具调用
 * 内用 MRTR elicitation 完成，不再有 {@code acf.confirm} 这样的合成工具挂在
 * 目录里。</p>
 *
 * @author bujidao
 */
public class AcfMcpToolRepository implements McpSyncToolRepository {

    private final CapabilityToolCatalog capabilityToolCatalog;
    private final AcfMcpToolMapper toolMapper;
    private final AcfMcpToolCallHandler toolCallHandler;
    private final CapabilityToolExportService toolExportService;

    public AcfMcpToolRepository(CapabilityToolCatalog capabilityToolCatalog,
                                AcfMcpToolMapper toolMapper,
                                AcfMcpToolCallHandler toolCallHandler) {
        this(capabilityToolCatalog, toolMapper, toolCallHandler, null);
    }

    public AcfMcpToolRepository(CapabilityToolCatalog capabilityToolCatalog,
                                AcfMcpToolMapper toolMapper,
                                AcfMcpToolCallHandler toolCallHandler,
                                CapabilityToolExportService toolExportService) {
        this.capabilityToolCatalog = capabilityToolCatalog;
        this.toolMapper = toolMapper;
        this.toolCallHandler = toolCallHandler;
        this.toolExportService = toolExportService;
    }

    @Override
    public ToolsPage list(McpRequestContext ctx, String cursor) {
        if (cursor != null) {
            throw McpException.invalidParams("Unknown cursor: " + cursor);
        }
        List<McpSchema.Tool> tools = new ArrayList<>(listVisibleTools(ctx));
        return ToolsPage.of(tools);
    }

    @Override
    public McpSyncTool find(McpRequestContext ctx, String name) {
        if (name == null) {
            return null;
        }
        for (CapabilityToolDescriptor descriptor : capabilityToolCatalog.listDeclared()) {
            if (descriptor.getCapabilityName().equals(name)) {
                // 不在这里声明 elicitation 必需能力：是否真的 elicite 取决于确认总开关
                // 与执行器当次的判定（默认关闭），静态声明会把本可直接成功的调用也
                // 以 -32021 拒掉；handler 在真正要 elicite 前会按当次客户端能力检查。
                return McpSyncTool.of(toolMapper.toTool(descriptor),
                        (requestContext, request) -> toolCallHandler.handle(requestContext, descriptor, request));
            }
        }
        return null;
    }

    private List<McpSchema.Tool> listVisibleTools(McpRequestContext ctx) {
        // 无论导出顺序如何，list 一律按能力名称升序返回：注释的口径以此为准，
        // 客户端拿到的顺序不依赖导出服务的内部顺序。
        if (toolExportService == null) {
            // 未接可见性服务时退回声明目录（与 legacy 静态列表一致），不做权限过滤。
            return capabilityToolCatalog.listDeclared().stream()
                    .map(toolMapper::toTool)
                    .sorted(Comparator.comparing(McpSchema.Tool::name))
                    .toList();
        }
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .context(AcfMcpContextSupport.fromTransport(ctx.transportContext(), null))
                .build();
        return toolExportService.export(query).stream()
                .map(toolMapper::toTool)
                .sorted(Comparator.comparing(McpSchema.Tool::name))
                .toList();
    }

}
