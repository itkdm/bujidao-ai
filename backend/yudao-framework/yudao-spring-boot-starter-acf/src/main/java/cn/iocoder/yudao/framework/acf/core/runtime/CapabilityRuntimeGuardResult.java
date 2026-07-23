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

    private CapabilityRuntimeGuardResult(boolean allowed, String guardCode, String errorCode,
                                         String reason, boolean retryable) {
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
    }

    public static CapabilityRuntimeGuardResult allowed(String guardCode) {
        return new CapabilityRuntimeGuardResult(true, guardCode, null, null, false);
    }

    public static CapabilityRuntimeGuardResult rejected(String guardCode, String errorCode,
                                                        String reason, boolean retryable) {
        return new CapabilityRuntimeGuardResult(false, guardCode, errorCode, reason, retryable);
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

}
