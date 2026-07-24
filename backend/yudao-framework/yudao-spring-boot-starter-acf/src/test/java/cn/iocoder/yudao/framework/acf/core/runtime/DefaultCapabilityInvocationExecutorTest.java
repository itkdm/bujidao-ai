package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import com.alibaba.ttl.TransmittableThreadLocal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
            CapabilityInvocationHandle handle = executor.submit(
                    () -> CapabilityResult.success("test.context.read", context.get(), null));
            assertThat(handle.await(1_000).getData()).isEqualTo("tenant-context");
            assertThat(handle.completion().toCompletableFuture().get().isTargetInvoked()).isTrue();
        } finally {
            context.remove();
        }
    }

    @Test
    void shouldRequestInterruptWithoutCompletingBeforeTargetTerminates() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (DefaultCapabilityInvocationExecutor executor = executor()) {
            CapabilityInvocationHandle handle = executor.submit(() -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        interruptObserved.countDown();
                    }
                }
                return CapabilityResult.success("test.timeout.wait", "late", null);
            });

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> handle.await(30)).isInstanceOf(TimeoutException.class);
            assertThat(handle.interrupt()).isEqualTo(CapabilityInvocationInterruptResult.INTERRUPT_REQUESTED);
            assertThat(interruptObserved.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(handle.completion().toCompletableFuture()).isNotDone();

            release.countDown();
            CapabilityInvocationCompletion completion = handle.completion().toCompletableFuture().get(1,
                    TimeUnit.SECONDS);
            assertThat(completion.getResult().getData()).isEqualTo("late");
            assertThat(completion.isTargetInvoked()).isTrue();
        }
    }

    @Test
    void shouldCancelQueuedInvocationBeforeStart() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch targetInvoked = new CountDownLatch(1);

        try (DefaultCapabilityInvocationExecutor executor =
                     new DefaultCapabilityInvocationExecutor(executorService, true)) {
            CapabilityInvocationHandle blocker = executor.submit(() -> {
                blockerStarted.countDown();
                releaseBlocker.await();
                return CapabilityResult.success("test.blocker", null, null);
            });
            assertThat(blockerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            CapabilityInvocationHandle queued = executor.submit(() -> {
                targetInvoked.countDown();
                return CapabilityResult.success("test.queued", null, null);
            });
            assertThat(queued.interrupt())
                    .isEqualTo(CapabilityInvocationInterruptResult.CANCELLED_BEFORE_START);

            CapabilityInvocationCompletion completion = queued.completion().toCompletableFuture().get(1,
                    TimeUnit.SECONDS);
            assertThat(completion.isTargetInvoked()).isFalse();
            assertThat(targetInvoked.getCount()).isOne();
            releaseBlocker.countDown();
            blocker.await(1_000);
        }
    }

    @Test
    void shouldExposeOriginalInvocationFailure() {
        IllegalStateException failure = new IllegalStateException("target failed");

        try (DefaultCapabilityInvocationExecutor executor = executor()) {
            CapabilityInvocationHandle handle = executor.submit(() -> {
                throw failure;
            });
            assertThatThrownBy(() -> handle.await(1_000)).hasCause(failure);
        }
    }

    private DefaultCapabilityInvocationExecutor executor() {
        return new DefaultCapabilityInvocationExecutor(Executors.newSingleThreadExecutor(), true);
    }

}
