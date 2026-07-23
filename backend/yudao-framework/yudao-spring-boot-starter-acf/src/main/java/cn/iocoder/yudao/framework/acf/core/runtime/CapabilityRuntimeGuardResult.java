package cn.iocoder.yudao.framework.acf.core.runtime;

import org.springframework.util.StringUtils;

/**
 * 运行时保护器获取结果
 *
 * @author bujidao
 */
public final class CapabilityRuntimeGuardResult {

    private final boolean allowed;
    private final String guardCode;
    private final String errorCode;
    private final String reason;
    private final boolean retryable;
    private final Object leaseState;

    private CapabilityRuntimeGuardResult(boolean allowed, String guardCode, String errorCode,
                                         String reason, boolean retryable, Object leaseState) {
        if (!StringUtils.hasText(guardCode)) {
            throw new IllegalArgumentException("Runtime guard code must not be blank");
        }
        if (!allowed && !StringUtils.hasText(errorCode)) {
            throw new IllegalArgumentException("Rejected runtime guard result requires an error code");
        }
        this.allowed = allowed;
        this.guardCode = guardCode;
        this.errorCode = errorCode;
        this.reason = reason;
        this.retryable = retryable;
        this.leaseState = leaseState;
    }

    public static CapabilityRuntimeGuardResult allowed(String guardCode) {
        return allowed(guardCode, null);
    }

    /**
     * 返回允许结果，并携带仅供本次 Guard 租约收口使用的私有状态。
     */
    public static CapabilityRuntimeGuardResult allowed(String guardCode, Object leaseState) {
        return new CapabilityRuntimeGuardResult(true, guardCode, null, null, false, leaseState);
    }

    public static CapabilityRuntimeGuardResult rejected(String guardCode, String errorCode,
                                                        String reason, boolean retryable) {
        return new CapabilityRuntimeGuardResult(false, guardCode, errorCode, reason, retryable, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getGuardCode() {
        return guardCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Object getLeaseState() {
        return leaseState;
    }

}
