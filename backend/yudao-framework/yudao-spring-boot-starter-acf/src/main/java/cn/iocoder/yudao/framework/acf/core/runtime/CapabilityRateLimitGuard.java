package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按租户和能力执行滑动窗口限流
 *
 * 默认实现只约束单个应用实例。多实例部署如需全局配额，应通过自定义 Guard
 * 接入 Redis、网关或其他共享限流设施。
 *
 * @author bujidao
 */
public class CapabilityRateLimitGuard implements CapabilityRuntimeGuard {

    public static final String CODE = "RATE_LIMIT";

    private static final int DEFAULT_CLEANUP_INTERVAL = 256;

    private final ConcurrentHashMap<String, WindowState> requestWindows = new ConcurrentHashMap<>();
    private final AtomicLong operationCount = new AtomicLong();
    private final Clock clock;
    private final int cleanupInterval;

    public CapabilityRateLimitGuard() {
        this(Clock.systemUTC(), DEFAULT_CLEANUP_INTERVAL);
    }

    CapabilityRateLimitGuard(Clock clock, int cleanupInterval) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (cleanupInterval <= 0) {
            throw new IllegalArgumentException("cleanupInterval must be greater than zero");
        }
        this.cleanupInterval = cleanupInterval;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean supports(CapabilityRuntimeGuardContext context) {
        return context.getPolicy().isRateLimitEnabled();
    }

    @Override
    public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
        int limit = context.getPolicy().getRateLimitCount();
        long windowMs = context.getPolicy().getRateLimitWindowSeconds() * 1_000L;
        long observedNow = clock.millis();
        RateLimitDecision decision = new RateLimitDecision();
        requestWindows.compute(key(context), (ignored, currentState) -> {
            WindowState state = currentState == null ? new WindowState(limit, windowMs, observedNow) : currentState;
            decision.result = state.acquire(limit, windowMs, observedNow);
            return state;
        });
        cleanupStaleWindows(observedNow);
        return Objects.requireNonNull(decision.result, "Rate limit decision must not be null");
    }

    int trackedWindowCount() {
        return requestWindows.size();
    }

    private void cleanupStaleWindows(long now) {
        if (operationCount.incrementAndGet() % cleanupInterval != 0) {
            return;
        }
        // 使用 computeIfPresent 与同 key 的请求串行，避免清理线程删除刚刚恢复活跃的窗口。
        for (String key : requestWindows.keySet()) {
            requestWindows.computeIfPresent(key,
                    (ignored, state) -> state.isExpired(now) ? null : state);
        }
    }

    private String key(CapabilityRuntimeGuardContext context) {
        Long tenantId = context.getInvocationContext().getTenantId();
        return String.valueOf(tenantId) + ":" + context.getDefinition().getName();
    }

    private static final class WindowState {

        private final Deque<Long> acceptedAt = new ArrayDeque<>();
        private int limit;
        private long windowMs;
        private long lastAccessAt;

        private WindowState(int limit, long windowMs, long now) {
            reset(limit, windowMs, now);
        }

        private CapabilityRuntimeGuardResult acquire(int currentLimit, long currentWindowMs, long observedNow) {
            long now = Math.max(observedNow, lastAccessAt);
            if (limit != currentLimit || windowMs != currentWindowMs) {
                // 策略动态变化时从新窗口开始，避免混用不同配额产生不可解释的结果。
                reset(currentLimit, currentWindowMs, now);
            }
            lastAccessAt = now;
            long windowStart = now - windowMs;
            while (!acceptedAt.isEmpty() && acceptedAt.peekFirst() <= windowStart) {
                acceptedAt.removeFirst();
            }
            if (acceptedAt.size() >= limit) {
                long retryAfterMs = Math.max(1, acceptedAt.peekFirst() + windowMs - now);
                return CapabilityRuntimeGuardResult.rejected(CODE,
                        AcfCapabilityErrorCodes.RUNTIME_RATE_LIMITED,
                        "Capability rate limit exceeded; retry after " + retryAfterMs + " ms", true);
            }
            acceptedAt.addLast(now);
            return CapabilityRuntimeGuardResult.allowed(CODE);
        }

        private void reset(int currentLimit, long currentWindowMs, long now) {
            acceptedAt.clear();
            limit = currentLimit;
            windowMs = currentWindowMs;
            lastAccessAt = now;
        }

        private boolean isExpired(long now) {
            return now >= lastAccessAt && now - lastAccessAt >= windowMs;
        }
    }

    private static final class RateLimitDecision {

        private CapabilityRuntimeGuardResult result;

    }

}
