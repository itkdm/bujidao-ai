package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;

/**
 * 能力运行时保护器
 *
 * acquire 成功后，调用方必须且只能执行一次 release、onSuccess 或 onFailure。
 * release 表示目标方法尚未执行时中止，不应被统计为目标执行失败。
 *
 * @author bujidao
 */
public interface CapabilityRuntimeGuard {

    String code();

    default int order() {
        return 0;
    }

    default boolean supports(CapabilityRuntimeGuardContext context) {
        return true;
    }

    CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context);

    default void release(CapabilityRuntimeGuardContext context) {
    }

    /**
     * 释放本次获取但尚未进入目标调用的运行时资源。
     *
     * @param leaseState {@link #acquire(CapabilityRuntimeGuardContext)} 返回的本次租约私有状态
     */
    default void release(CapabilityRuntimeGuardContext context, Object leaseState) {
        release(context);
    }

    default void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result) {
        release(context);
    }

    /**
     * 在目标调用成功后收口本次租约。需要区分并发调用状态的 Guard 应覆写该方法。
     */
    default void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result, Object leaseState) {
        onSuccess(context, result);
    }

    default void onFailure(CapabilityRuntimeGuardContext context, CapabilityResult result, Throwable cause) {
        release(context);
    }

    /**
     * 在目标调用失败后收口本次租约。leaseState 只属于对应 acquire，不能跨调用复用。
     */
    default void onFailure(CapabilityRuntimeGuardContext context, CapabilityResult result,
                           Throwable cause, Object leaseState) {
        onFailure(context, result, cause);
    }

}
