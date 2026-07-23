package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按租户和能力限制单个应用实例内的并发执行数量
 *
 * 多实例部署如需全局并发上限，应由业务方提供基于共享存储的 Guard 实现替换本实现。
 *
 * @author bujidao
 */
public class CapabilityConcurrencyGuard implements CapabilityRuntimeGuard {

    public static final String CODE = "CONCURRENCY";

    private final ConcurrentHashMap<String, Integer> inflightCounters = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(CapabilityRuntimeGuardContext context) {
        return context.getPolicy().getMaxConcurrency() != null;
    }

    @Override
    public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
        String key = key(context);
        int limit = context.getPolicy().getMaxConcurrency();
        AtomicBoolean acquired = new AtomicBoolean();
        // 计数判断与递增必须在同一次 compute 中完成，避免并发请求同时越过上限。
        inflightCounters.compute(key, (ignored, currentValue) -> {
            int current = currentValue == null ? 0 : currentValue;
            if (current >= limit) {
                return current;
            }
            acquired.set(true);
            return current + 1;
        });
        if (!acquired.get()) {
            return CapabilityRuntimeGuardResult.rejected(CODE,
                    AcfCapabilityErrorCodes.RUNTIME_CONCURRENCY_REJECTED,
                    "Capability concurrency limit exceeded", false);
        }
        return CapabilityRuntimeGuardResult.allowed(CODE);
    }

    @Override
    public void release(CapabilityRuntimeGuardContext context) {
        inflightCounters.computeIfPresent(key(context), (ignored, current) -> current <= 1 ? null : current - 1);
    }

    private String key(CapabilityRuntimeGuardContext context) {
        Long tenantId = context.getInvocationContext().getTenantId();
        return String.valueOf(tenantId) + ":" + context.getDefinition().getName();
    }

}
