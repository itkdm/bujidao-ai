package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import com.alibaba.ttl.TtlCallable;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认能力目标方法执行器
 *
 * 使用 ACF 专用有界线程池，避免能力调用占用通用异步线程池；提交任务时通过 TTL
 * 传递芋道现有的租户、安全等线程上下文。超时会取消 Future 并发送中断信号，
 * 业务能力仍应正确响应线程中断，不能把超时理解为 JVM 能够强制终止任意代码。
 *
 * @author bujidao
 */
public final class DefaultCapabilityInvocationExecutor implements CapabilityInvocationExecutor, AutoCloseable {

    private static final int KEEP_ALIVE_SECONDS = 60;

    private static final CapabilityInvocationExecutor SHARED_INSTANCE =
            new DefaultCapabilityInvocationExecutor(createExecutor(true), false);

    private final ExecutorService executorService;
    private final boolean shutdownOnClose;

    /**
     * 创建由 Spring Bean 生命周期管理的默认执行器。
     */
    public DefaultCapabilityInvocationExecutor() {
        this(createExecutor(false), true);
    }

    DefaultCapabilityInvocationExecutor(ExecutorService executorService, boolean shutdownOnClose) {
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.shutdownOnClose = shutdownOnClose;
    }

    /**
     * 兼容直接构造 CapabilityExecutor 的场景，共享实例使用守护线程且不会由单个调用方关闭。
     */
    public static CapabilityInvocationExecutor shared() {
        return SHARED_INSTANCE;
    }

    @Override
    public CapabilityResult invoke(Callable<CapabilityResult> invocation, int timeoutMs) throws Exception {
        Objects.requireNonNull(invocation, "invocation");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be greater than zero");
        }
        Future<CapabilityResult> future = executorService.submit(TtlCallable.get(invocation));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            cancel(future);
            TimeoutException timeoutException = new TimeoutException(
                    "Capability invocation timed out after " + timeoutMs + " ms");
            timeoutException.initCause(exception);
            throw timeoutException;
        } catch (ExecutionException exception) {
            // 保留标准异步包装，由统一异常分类器剥离，避免 SPI 被迫声明并捕获任意 Error。
            throw exception;
        } catch (InterruptedException exception) {
            cancel(future);
            Thread.currentThread().interrupt();
            throw exception;
        }
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

    private void cancel(Future<?> future) {
        future.cancel(true);
        // 已在队列中但尚未启动的超时任务应立即移除，避免无效任务继续占用有界队列。
        if (executorService instanceof ThreadPoolExecutor threadPoolExecutor && future instanceof Runnable task) {
            threadPoolExecutor.remove(task);
        }
    }

    private static ExecutorService createExecutor(boolean daemon) {
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, Math.min(processors, 8));
        int maximumPoolSize = Math.max(16, Math.min(processors * 4, 64));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize, maximumPoolSize, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new CapabilityThreadFactory(daemon), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class CapabilityThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();
        private final boolean daemon;

        private CapabilityThreadFactory(boolean daemon) {
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "acf-invocation-" + sequence.incrementAndGet());
            thread.setDaemon(daemon);
            return thread;
        }
    }

}
