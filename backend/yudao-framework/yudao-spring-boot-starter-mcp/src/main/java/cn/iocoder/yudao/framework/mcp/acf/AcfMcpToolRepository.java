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
import java.util.List;

/**
 * 根据代码中声明的 ACF 能力构造 MCP Tool 目录（modern repository 模式）。
 *
 * <p>list：按当前调用身份返回可见工具。可见性由 {@link CapabilityToolExportService}
 * 按 transport context 里的身份判定，顺序与 {@link CapabilityToolCatalog} 一致
 * （能力名称升序，见 CapabilityRegistry），确定性；不支持分页，任何非空 cursor
 * 都按 SDK 约定以 invalid params 拒绝。{@code acf.confirm} 工具（如果启用）追加在末尾。</p>
 *
 * <p>find：只按名称查找，不做可见性过滤——无权限的调用仍要进 ACF 执行链，
 * 由治理层返回权限错误，而不是在这里伪装成“工具不存在”。未知名称返回 null，
 * SDK 会答复 Unknown tool。</p>
 *
 * @author bujidao
 */
public class AcfMcpToolRepository implements McpSyncToolRepository {

    private final CapabilityToolCatalog capabilityToolCatalog;
    private final AcfMcpToolMapper toolMapper;
    private final AcfMcpToolCallHandler toolCallHandler;
    private final CapabilityToolExportService toolExportService;
    private final AcfMcpConfirmationTool confirmationTool;

    public AcfMcpToolRepository(CapabilityToolCatalog capabilityToolCatalog,
                                AcfMcpToolMapper toolMapper,
                                AcfMcpToolCallHandler toolCallHandler) {
        this(capabilityToolCatalog, toolMapper, toolCallHandler, null, null);
    }

    public AcfMcpToolRepository(CapabilityToolCatalog capabilityToolCatalog,
                                AcfMcpToolMapper toolMapper,
                                AcfMcpToolCallHandler toolCallHandler,
                                CapabilityToolExportService toolExportService) {
        this(capabilityToolCatalog, toolMapper, toolCallHandler, toolExportService, null);
    }

    public AcfMcpToolRepository(CapabilityToolCatalog capabilityToolCatalog,
                                AcfMcpToolMapper toolMapper,
                                AcfMcpToolCallHandler toolCallHandler,
                                CapabilityToolExportService toolExportService,
                                AcfMcpConfirmationTool confirmationTool) {
        this.capabilityToolCatalog = capabilityToolCatalog;
        this.toolMapper = toolMapper;
        this.toolCallHandler = toolCallHandler;
        this.toolExportService = toolExportService;
        this.confirmationTool = confirmationTool;
    }

    @Override
    public ToolsPage list(McpRequestContext ctx, String cursor) {
        if (cursor != null) {
            throw McpException.invalidParams("Unknown cursor: " + cursor);
        }
        List<McpSchema.Tool> tools = new ArrayList<>(listVisibleTools(ctx));
        if (confirmationTool != null) {
            tools.add(confirmationTool.tool());
        }
        return ToolsPage.of(tools);
    }

    @Override
    public McpSyncTool find(McpRequestContext ctx, String name) {
        if (name == null) {
            return null;
        }
        // 声明目录优先：若某声明能力恰好重名 acf.confirm，不应被确认工具遮蔽。
        for (CapabilityToolDescriptor descriptor : capabilityToolCatalog.listDeclared()) {
            if (descriptor.getCapabilityName().equals(name)) {
                return McpSyncTool.of(toolMapper.toTool(descriptor),
                        (requestContext, request) -> toolCallHandler.handle(requestContext, descriptor, request));
            }
        }
        if (confirmationTool != null && AcfMcpConfirmationTool.NAME.equals(name)) {
            return McpSyncTool.of(confirmationTool.tool(), confirmationTool::handle);
        }
        return null;
    }

    private List<McpSchema.Tool> listVisibleTools(McpRequestContext ctx) {
        if (toolExportService == null) {
            // 未接可见性服务时退回声明目录（与 legacy 静态列表一致），不做权限过滤。
            return capabilityToolCatalog.listDeclared().stream()
                    .map(toolMapper::toTool)
                    .toList();
        }
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .context(AcfMcpContextSupport.fromTransport(ctx.transportContext(), null))
                .build();
        return toolExportService.export(query).stream()
                .map(toolMapper::toTool)
                .toList();
    }

}
