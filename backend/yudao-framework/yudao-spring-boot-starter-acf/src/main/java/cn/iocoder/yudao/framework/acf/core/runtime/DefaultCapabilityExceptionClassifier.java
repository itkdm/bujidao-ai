package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import jakarta.validation.ConstraintViolationException;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 默认能力执行异常分类器
 *
 * 默认实现只对能够确定语义的异常开放重试标志。未知异常可能来自已经产生副作用的业务方法，
 * 因此默认不可重试，调用方不能仅凭一次未知失败重复执行。
 *
 * @author bujidao
 */
public class DefaultCapabilityExceptionClassifier implements CapabilityExceptionClassifier {

    @Override
    public CapabilityExceptionClassification classify(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof IllegalArgumentException || cause instanceof ConstraintViolationException) {
            return classification(AcfCapabilityErrorCodes.BAD_REQUEST, cause, false);
        }
        if (cause instanceof TimeoutException) {
            return CapabilityExceptionClassification.of(AcfCapabilityErrorCodes.RUNTIME_TIMEOUT,
                    "Capability invocation timed out", true, cause);
        }
        if (cause instanceof RejectedExecutionException) {
            return CapabilityExceptionClassification.of(AcfCapabilityErrorCodes.RUNTIME_EXECUTOR_REJECTED,
                    "Capability invocation executor is saturated", true, cause);
        }
        return classification(AcfCapabilityErrorCodes.INVOKE_ERROR, cause, false);
    }

    private CapabilityExceptionClassification classification(String errorCode, Throwable cause, boolean retryable) {
        String message = cause == null || !StringUtils.hasText(cause.getMessage())
                ? cause == null ? "Unknown capability execution error" : cause.getClass().getSimpleName()
                : cause.getMessage();
        return CapabilityExceptionClassification.of(errorCode, message, retryable, cause);
    }

    /**
     * 反射调用和异步执行通常会为真实业务异常增加包装，这里只剥离已知透明包装层，
     * 不沿任意 cause 链下钻，避免错误隐藏具备业务语义的外层异常。
     */
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
