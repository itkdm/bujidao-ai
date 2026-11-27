package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityVisibilityService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncListFilter;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Legacy（2025-11-25）腿的 tools/list 可见性过滤。
 *
 * <p>用 SDK 自带的 {@code StatelessSyncSpecification.addToolFilter} 机制挂在 legacy
 * 服务端上：只决定某工具是否出现在 tools/list 里，不碰 tools/call——被隐藏的工具
 * 仍可按名调用，调用时由 {@code AcfMcpToolCallHandler.handleLegacy} 的 ACF 执行链
 * 二次校验权限（SDK 的 {@code McpSyncListFilter} 契约本身就是这么定的，与 main
 * 的 {@code AcfMcpToolsListFilter} 语义一致）。Modern 腿不受影响：modern 的
 * tools/list 由 {@code AcfMcpToolRepository} 在仓库内过滤，两边互不干扰。</p>
 *
 * <p>为什么不用恢复 filter（方案 a）：filter 跑在安全链里、比分流器早，要拦截 legacy
 * 的 tools/list 就得在分流前再实现一遍分流判定（头 + 体），与分流器各执一份路由
 * 真相；一旦两边判定不一致，modern 的 tools/list 会被 filter 抢答、或 legacy
 * 的漏网。本过滤器挂在 legacy 服务端内部，只对已分流到 legacy 的请求生效，
 * 路由只有分流器一处真相。</p>
 *
 * <p>身份来源与 modern 仓库完全一致：legacy transport 用的是同一个
 * contextExtractor bean，transportContext 里同样有 USER_ID / TENANT_ID 等，
 * {@code AcfMcpContextSupport.fromTransport} 产出的 CapabilityContext 与 modern
 * 相同；逐工具调 {@code CapabilityVisibilityService.getVisible(name, query)}，
 * 与 export() 全量导出的策略求值次数相同（每个声明工具一次），不需要缓存。
 * 注意这里的“一致”只覆盖可见性判定次数与身份来源，不覆盖工具定义本身：
 * export() 可能返回治理层替换后的 definition，而 legacy 静态规格永远用
 * catalog 原始 descriptor——可能分叉的也只有 _meta（risk/idempotency/
 * confirmation）与 readOnly/idempotent hints，名/描述/schema 受
 * {@code CapabilityPolicyChain.validateOverlay} 校验保证同源（见
 * {@link AcfMcpToolSpecificationProvider} 的等价边界说明）。</p>
 *
 * <p>可见性服务可为空（与 {@code AcfMcpToolRepository} 的 fallback 一致）：
 * 为空时不过滤、全部工具可见；不用 {@code @ConditionalOnBean} 建 bean 是刻意的——
 * 可见性服务由另一个自动配置提供，跨配置的 {@code @ConditionalOnBean} 有顺序风险，
 * 这里沿用同配置里 repository 的 {@code ObjectProvider} 模式。</p>
 *
 * @author bujidao
 */
public class AcfMcpLegacyToolsListFilter implements McpSyncListFilter<McpSchema.Tool> {

    private final CapabilityVisibilityService visibilityService;

    /**
     * @param visibilityService 可见性服务；为空时不过滤（全部可见）
     */
    public AcfMcpLegacyToolsListFilter(CapabilityVisibilityService visibilityService) {
        this.visibilityService = visibilityService;
    }

    @Override
    public boolean isVisible(McpTransportContext transportContext, McpSchema.Tool tool) {
        if (tool == null || tool.name() == null) {
            return false;
        }
        if (visibilityService == null) {
            return true;
        }
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .context(AcfMcpContextSupport.fromTransport(transportContext, null))
                .build();
        return visibilityService.getVisible(tool.name(), query).isPresent();
    }

}
