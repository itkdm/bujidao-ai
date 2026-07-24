package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;

/**
 * Terminal result of a capability invocation.
 *
 * @author bujidao
 */
public final class CapabilityInvocationCompletion {

    private final CapabilityResult result;
    private final Throwable failure;
    private final boolean targetInvoked;

    private CapabilityInvocationCompletion(CapabilityResult result, Throwable failure, boolean targetInvoked) {
        this.result = result;
        this.failure = failure;
        this.targetInvoked = targetInvoked;
    }

    public static CapabilityInvocationCompletion completed(CapabilityResult result) {
        return new CapabilityInvocationCompletion(result, null, true);
    }

    public static CapabilityInvocationCompletion failed(Throwable failure) {
        return new CapabilityInvocationCompletion(null, failure, true);
    }

    public static CapabilityInvocationCompletion cancelledBeforeStart() {
        return new CapabilityInvocationCompletion(null, null, false);
    }

    public CapabilityResult getResult() {
        return result;
    }

    public Throwable getFailure() {
        return failure;
    }

    public boolean isTargetInvoked() {
        return targetInvoked;
    }

}
