package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.mcp.tool.McpToolSpecificationProvider;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import java.util.List;

/**
 * 根据代码中声明的 ACF 能力构造 legacy MCP Tool 规格（MCP 2025-11-25，stateless），
 * 供 legacy 服务端（{@code McpStatelessSyncServer}）使用。
 *
 * <p>同源：规格里的每个 legacy Tool 都是 {@link AcfMcpToolMapper} 从同一份
 * {@code CapabilityToolDescriptor} 产出的 modern Tool 再经
 * {@link AcfMcpLegacyToolAdapter#toLegacyTool(io.modelcontextprotocol.modern.McpSchema.Tool)}
 * 字段级转写而来；工具调用走 {@link AcfMcpToolCallHandler#handleLegacy}，与 modern 腿
 * 共用同一条 ACF 执行链（权限、幂等、审计），两边行为一致。本类不维护第二份工具清单，
 * 不复制工具定义。</p>
 *
 * <p>等价边界（tools/list）：两腿的“等价”只在当前无覆盖定义的策略集下成立——
 * modern 腿的 {@code export()} 用治理层返回的替换后 definition，legacy 腿的静态
 * 规格永远用 catalog 原始 descriptor；当前已装的两个 policy
 * （{@code CapabilityPermissionPolicy}、
 * {@code AcfCapabilityRuntimeStatusPolicy}）的 {@code allow()} 都传 null 覆盖
 * （不替换 definition），所以目前两边定义一致，一旦有 policy 开始返回替换
 * definition，两腿的 tools/list 定义就会分叉（调用仍共用同一条 ACF 执行链，
 * 不受影响）。顺序已对齐：legacy 规格构建时按名升序，与 modern 仓库一致；
 * cursor 语义仍不同（modern 遇非空 cursor 以 -32602 拒绝，legacy 忽略 cursor
 * 返回全量），见采用指南“双协议分流”。</p>
 *
 * <p>该静态规格只用于 SDK 的 tools/call 路由，不做可见性过滤：tools/list 的按用户
 * 过滤由 D3 的 {@link AcfMcpLegacyToolsListFilter}（SDK 自带的
 * {@code StatelessSyncSpecification.addToolFilter} 机制，只作用于 legacy 腿）
 * 完成；无过滤（或无可见性服务）时 legacy 腿的 tools/list 返回全部声明工具，与
 * modern 仓库缺可见性服务时的 fallback 一致。</p>
 *
 * <p>确认不走独立工具：本类不把已删除的 {@code acf.confirm} 加进规格列表；需要确认的
 * 能力在 legacy 腿上以明确的 CONFIRMATION_UNAVAILABLE 错误结果返回（见
 * {@link AcfMcpToolCallHandler#handleLegacy}），不静默执行，确认流程留给后续块决定。</p>
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
                .tool(AcfMcpLegacyToolAdapter.toLegacyTool(toolMapper.toTool(descriptor)))
                .callHandler((transportContext, request) ->
                        toolCallHandler.handleLegacy(transportContext, descriptor, request))
                .build();
    }

}
