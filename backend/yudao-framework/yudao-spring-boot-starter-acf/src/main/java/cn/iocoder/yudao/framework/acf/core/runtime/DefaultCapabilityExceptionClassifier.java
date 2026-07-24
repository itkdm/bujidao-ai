package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import jakarta.validation.ConstraintViolationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Default exception classifier with stable public messages.
 *
 * Unknown exception messages are never exposed because they may contain SQL,
 * paths, service addresses, credentials, or private business data.
 *
 * @author bujidao
 */
public class DefaultCapabilityExceptionClassifier implements CapabilityExceptionClassifier {

    @Override
    public CapabilityExceptionClassification classify(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof IllegalArgumentException || cause instanceof ConstraintViolationException) {
            return CapabilityExceptionClassification.of(AcfCapabilityErrorCodes.BAD_REQUEST,
                    "Capability request is invalid", false, cause);
        }
        if (cause instanceof TimeoutException) {
            return CapabilityExceptionClassification.of(AcfCapabilityErrorCodes.RUNTIME_TIMEOUT,
                    "Capability invocation timed out", true, cause);
        }
        if (cause instanceof RejectedExecutionException) {
            return CapabilityExceptionClassification.of(AcfCapabilityErrorCodes.RUNTIME_EXECUTOR_REJECTED,
                    "Capability invocation executor is saturated", true, cause);
        }
        return CapabilityExceptionClassification.of(AcfCapabilityErrorCodes.INVOKE_ERROR,
                "Capability invocation failed", false, cause);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (true) {
            Throwable unwrapped = unwrapOneLevel(current);
            if (unwrapped == current) {
                return current;
            }
            current = unwrapped;
        }
    }

    private Throwable unwrapOneLevel(Throwable throwable) {
        if (throwable instanceof InvocationTargetException exception && exception.getTargetException() != null) {
            return exception.getTargetException();
        }
        if (throwable instanceof ExecutionException exception && exception.getCause() != null) {
            return exception.getCause();
        }
        if (throwable instanceof CompletionException exception && exception.getCause() != null) {
            return exception.getCause();
        }
        if (throwable instanceof UndeclaredThrowableException exception
                && exception.getUndeclaredThrowable() != null) {
            return exception.getUndeclaredThrowable();
        }
        return throwable;
    }

}
