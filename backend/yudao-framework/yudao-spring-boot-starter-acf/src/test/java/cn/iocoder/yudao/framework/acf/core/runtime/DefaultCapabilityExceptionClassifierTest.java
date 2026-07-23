package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCapabilityExceptionClassifierTest {

    private final DefaultCapabilityExceptionClassifier classifier = new DefaultCapabilityExceptionClassifier();

    @Test
    void shouldClassifyArgumentFailureAsNonRetryableBadRequest() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid amount");

        CapabilityExceptionClassification result = classifier.classify(
                new InvocationTargetException(new CompletionException(cause)));

        assertThat(result.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.BAD_REQUEST);
        assertThat(result.getMessage()).isEqualTo("invalid amount");
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getCause()).isSameAs(cause);
    }

    @Test
    void shouldClassifyTimeoutAsRetryableRuntimeFailure() {
        TimeoutException cause = new TimeoutException("slow downstream");

        CapabilityExceptionClassification result = classifier.classify(cause);

        assertThat(result.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_TIMEOUT);
        assertThat(result.getMessage()).isEqualTo("Capability invocation timed out");
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getCause()).isSameAs(cause);
    }

    @Test
    void shouldKeepUnknownFailureNonRetryable() {
        IllegalStateException cause = new IllegalStateException("inventory unavailable");

        CapabilityExceptionClassification result = classifier.classify(cause);

        assertThat(result.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.INVOKE_ERROR);
        assertThat(result.getMessage()).isEqualTo("inventory unavailable");
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getCause()).isSameAs(cause);
    }

    @Test
    void shouldClassifyExecutorSaturationAsRetryableRuntimeFailure() {
        RejectedExecutionException cause = new RejectedExecutionException("queue full");

        CapabilityExceptionClassification result = classifier.classify(cause);

        assertThat(result.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_EXECUTOR_REJECTED);
        assertThat(result.getMessage()).isEqualTo("Capability invocation executor is saturated");
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getCause()).isSameAs(cause);
    }

    @Test
    void shouldProvideStableMessageWhenThrowableIsMissing() {
        CapabilityExceptionClassification result = classifier.classify(null);

        assertThat(result.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.INVOKE_ERROR);
        assertThat(result.getMessage()).isEqualTo("Unknown capability execution error");
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getCause()).isNull();
    }

}
