package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import com.alibaba.ttl.TtlRunnable;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default isolated executor for capability targets.
 *
 * Cancellation only requests interruption. Terminal completion is controlled by
 * the worker wrapper and is never completed by Future.cancel for a running task.
 *
 * @author bujidao
 */
public final class DefaultCapabilityInvocationExecutor implements CapabilityInvocationExecutor, AutoCloseable {

    private static final int KEEP_ALIVE_SECONDS = 60;

    private final ExecutorService executorService;
    private final boolean shutdownOnClose;

    public DefaultCapabilityInvocationExecutor() {
        this(createExecutor(), true);
    }

    DefaultCapabilityInvocationExecutor(ExecutorService executorService, boolean shutdownOnClose) {
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.shutdownOnClose = shutdownOnClose;
    }

    @Override
    public CapabilityInvocationHandle submit(Callable<CapabilityResult> invocation) {
        Objects.requireNonNull(invocation, "invocation");
        DefaultCapabilityInvocationHandle handle = new DefaultCapabilityInvocationHandle(executorService);
        Future<?> future = executorService.submit(TtlRunnable.get(() -> handle.run(invocation)));
        handle.attach(future);
        return handle;
    }

    @Override
    public void close() {
        if (!shutdownOnClose) {
            return;
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ExecutorService createExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, Math.min(processors, 8));
        int maximumPoolSize = Math.max(16, Math.min(processors * 4, 64));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize, maximumPoolSize, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new CapabilityThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class DefaultCapabilityInvocationHandle implements CapabilityInvocationHandle {

        private final ExecutorService executorService;
        private final AtomicReference<InvocationState> state = new AtomicReference<>(InvocationState.SUBMITTED);
        private final AtomicReference<Future<?>> future = new AtomicReference<>();
        private final CompletableFuture<CapabilityInvocationCompletion> completion = new CompletableFuture<>();

        private DefaultCapabilityInvocationHandle(ExecutorService executorService) {
            this.executorService = executorService;
        }

        private void attach(Future<?> submittedFuture) {
            future.set(submittedFuture);
            if (state.get() == InvocationState.CANCELLED_BEFORE_START) {
                cancelFuture(submittedFuture, false);
            }
        }

        private void run(Callable<CapabilityResult> invocation) {
            if (!state.compareAndSet(InvocationState.SUBMITTED, InvocationState.RUNNING)) {
                return;
            }
            CapabilityInvocationCompletion terminal;
            try {
                terminal = CapabilityInvocationCompletion.completed(invocation.call());
            } catch (Throwable throwable) {
                terminal = CapabilityInvocationCompletion.failed(throwable);
            }
            state.set(InvocationState.TERMINATED);
            completion.complete(terminal);
        }

        @Override
        public CapabilityResult await(int timeoutMs)
                throws InterruptedException, ExecutionException, TimeoutException {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("timeoutMs must be greater than zero");
            }
            CapabilityInvocationCompletion terminal = completion.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (!terminal.isTargetInvoked()) {
                throw new ExecutionException(new IllegalStateException("Capability invocation was cancelled"));
            }
            if (terminal.getFailure() != null) {
                throw new ExecutionException(terminal.getFailure());
            }
            return terminal.getResult();
        }

        @Override
        public CapabilityInvocationInterruptResult interrupt() {
            while (true) {
                InvocationState current = state.get();
                if (current == InvocationState.SUBMITTED) {
                    if (!state.compareAndSet(InvocationState.SUBMITTED, InvocationState.CANCELLED_BEFORE_START)) {
                        continue;
                    }
                    Future<?> submittedFuture = future.get();
                    if (submittedFuture != null) {
                        cancelFuture(submittedFuture, false);
                    }
                    completion.complete(CapabilityInvocationCompletion.cancelledBeforeStart());
                    return CapabilityInvocationInterruptResult.CANCELLED_BEFORE_START;
                }
                if (current == InvocationState.RUNNING) {
                    Future<?> submittedFuture = future.get();
                    if (submittedFuture != null) {
                        submittedFuture.cancel(true);
                    }
                    return CapabilityInvocationInterruptResult.INTERRUPT_REQUESTED;
                }
                return CapabilityInvocationInterruptResult.ALREADY_TERMINATED;
            }
        }

        @Override
        public CompletionStage<CapabilityInvocationCompletion> completion() {
            return completion;
        }

        private void cancelFuture(Future<?> submittedFuture, boolean mayInterruptIfRunning) {
            submittedFuture.cancel(mayInterruptIfRunning);
            if (executorService instanceof ThreadPoolExecutor threadPoolExecutor
                    && submittedFuture instanceof Runnable task) {
                threadPoolExecutor.remove(task);
            }
        }

    }

    private enum InvocationState {
        SUBMITTED,
        RUNNING,
        CANCELLED_BEFORE_START,
        TERMINATED
    }

    private static final class CapabilityThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "acf-invocation-" + sequence.incrementAndGet());
        }

    }

}
