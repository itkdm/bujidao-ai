package cn.iocoder.yudao.framework.acf.core.runtime;

import org.springframework.util.StringUtils;

/**
 * Safe public classification of a capability execution exception.
 *
 * The public message may be returned to protocol callers and persisted in audit
 * records. The original cause is retained only for trusted in-process handling.
 *
 * @author bujidao
 */
public final class CapabilityExceptionClassification {

    private final String errorCode;
    private final String publicMessage;
    private final boolean retryable;
    private final Throwable cause;

    private CapabilityExceptionClassification(String errorCode, String publicMessage,
                                              boolean retryable, Throwable cause) {
        if (!StringUtils.hasText(errorCode)) {
            throw new IllegalArgumentException("Capability exception error code must not be blank");
        }
        if (!StringUtils.hasText(publicMessage)) {
            throw new IllegalArgumentException("Capability exception public message must not be blank");
        }
        this.errorCode = errorCode;
        this.publicMessage = publicMessage;
        this.retryable = retryable;
        this.cause = cause;
    }

    public static CapabilityExceptionClassification of(String errorCode, String publicMessage,
                                                       boolean retryable, Throwable cause) {
        return new CapabilityExceptionClassification(errorCode, publicMessage, retryable, cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getPublicMessage() {
        return publicMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Throwable getCause() {
        return cause;
    }

}
