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

    default void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result) {
        release(context);
    }

    default void onFailure(CapabilityRuntimeGuardContext context, CapabilityResult result, Throwable cause) {
        release(context);
    }

}
