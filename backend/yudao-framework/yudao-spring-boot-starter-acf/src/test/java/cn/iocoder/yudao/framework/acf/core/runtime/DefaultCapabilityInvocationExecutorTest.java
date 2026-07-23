package cn.iocoder.yudao.framework.acf.core.runtime;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCapabilityInvocationExecutorTest {

    @Test
    void shouldPropagateTransmittableThreadLocalContext() throws Exception {
        TransmittableThreadLocal<String> context = new TransmittableThreadLocal<>();
        context.set("tenant-context");

        try (DefaultCapabilityInvocationExecutor executor = executor()) {
            assertThat(executor.invoke(context::get, 1_000)).isEqualTo("tenant-context");
        } finally {
            context.remove();
        }
    }

    @Test
    void shouldCancelInvocationWhenWaitingTimesOut() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        try (DefaultCapabilityInvocationExecutor executor = executor()) {
            assertThatThrownBy(() -> executor.invoke(() -> {
                started.countDown();
                try {
                    Thread.sleep(10_000);
                    return "late";
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    throw exception;
                }
            }, 30)).isInstanceOf(TimeoutException.class)
                    .hasMessage("Capability invocation timed out after 30 ms");

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldExposeOriginalInvocationFailure() throws Exception {
        IllegalStateException failure = new IllegalStateException("target failed");

        try (DefaultCapabilityInvocationExecutor executor = executor()) {
            assertThatThrownBy(() -> executor.invoke(() -> {
                throw failure;
            }, 1_000)).isInstanceOf(ExecutionException.class)
                    .hasCause(failure);
        }
    }

    private DefaultCapabilityInvocationExecutor executor() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        return new DefaultCapabilityInvocationExecutor(executorService, true);
    }

}
