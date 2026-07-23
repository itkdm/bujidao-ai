package cn.iocoder.yudao.framework.acf.core.runtime;

import org.springframework.util.StringUtils;

/**
 * 能力执行异常分类结果
 *
 * @author bujidao
 */
public final class CapabilityExceptionClassification {

    private final String errorCode;
    private final String message;
    private final boolean retryable;
    private final Throwable cause;

    private CapabilityExceptionClassification(String errorCode, String message, boolean retryable, Throwable cause) {
        if (!StringUtils.hasText(errorCode)) {
            throw new IllegalArgumentException("Capability exception error code must not be blank");
        }
        this.errorCode = errorCode;
        this.message = StringUtils.hasText(message) ? message : "Unknown capability execution error";
        this.retryable = retryable;
        this.cause = cause;
    }

    public static CapabilityExceptionClassification of(String errorCode, String message,
                                                       boolean retryable, Throwable cause) {
        return new CapabilityExceptionClassification(errorCode, message, retryable, cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Throwable getCause() {
        return cause;
    }

}
