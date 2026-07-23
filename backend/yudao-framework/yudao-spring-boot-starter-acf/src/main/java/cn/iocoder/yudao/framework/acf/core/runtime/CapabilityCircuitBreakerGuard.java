package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按租户和能力执行连续失败熔断
 *
 * 默认实现维护单应用实例内的 CLOSED、OPEN、HALF_OPEN 状态机。多实例部署如需共享
 * 熔断状态，应替换为基于集中式存储或服务治理设施的 Guard。
 *
 * @author bujidao
 */
public class CapabilityCircuitBreakerGuard implements CapabilityRuntimeGuard {

    public static final String CODE = "CIRCUIT_BREAKER";

    private static final int DEFAULT_CLEANUP_INTERVAL = 256;
    private static final long MIN_IDLE_RETENTION_MS = 60_000L;

    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final AtomicLong operationCount = new AtomicLong();
    private final Clock clock;
    private final int cleanupInterval;

    public CapabilityCircuitBreakerGuard() {
        this(Clock.systemUTC(), DEFAULT_CLEANUP_INTERVAL);
    }

    CapabilityCircuitBreakerGuard(Clock clock, int cleanupInterval) {
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
        // 熔断应尽早拒绝，避免继续占用并发名额或消耗限流配额。
        return 50;
    }

    @Override
    public boolean supports(CapabilityRuntimeGuardContext context) {
        return context.getPolicy().isCircuitBreakerEnabled();
    }

    @Override
    public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
        CircuitDecision decision = new CircuitDecision();
        long observedNow = clock.millis();
        circuits.compute(key(context), (ignored, currentState) -> {
            CircuitState state = currentState == null ? new CircuitState() : currentState;
            decision.result = state.acquire(context.getPolicy(), observedNow);
            return state;
        });
        cleanupStaleCircuits(observedNow);
        return Objects.requireNonNull(decision.result, "Circuit breaker decision must not be null");
    }

    @Override
    public void release(CapabilityRuntimeGuardContext context, Object leaseState) {
        CircuitPermit permit = permit(leaseState);
        if (permit == null || !permit.halfOpenProbe()) {
            return;
        }
        circuits.computeIfPresent(key(context),
                (ignored, state) -> state.releaseHalfOpenPermit(permit, clock.millis()));
    }

    @Override
    public void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result, Object leaseState) {
        complete(context, permit(leaseState), false);
    }

    @Override
    public void onFailure(CapabilityRuntimeGuardContext context, CapabilityResult result,
                          Throwable cause, Object leaseState) {
        complete(context, permit(leaseState), shouldRecordFailure(result, cause));
    }

    int trackedCircuitCount() {
        return circuits.size();
    }

    private void complete(CapabilityRuntimeGuardContext context, CircuitPermit permit, boolean recordFailure) {
        if (permit == null) {
            return;
        }
        circuits.computeIfPresent(key(context), (ignored, state) ->
                state.complete(permit, recordFailure, clock.millis()));
    }

    /**
     * 默认只记录已经进入目标执行阶段的系统异常。业务标准失败以及本地线程池、并发、
     * 限流等拒绝不代表目标能力失去健康度，不应推动熔断器打开。
     */
    protected boolean shouldRecordFailure(CapabilityResult result, Throwable cause) {
        if (cause == null) {
            return false;
        }
        String errorCode = result == null ? null : result.getErrorCode();
        return !AcfCapabilityErrorCodes.BAD_REQUEST.equals(errorCode)
                && !AcfCapabilityErrorCodes.RUNTIME_EXECUTOR_REJECTED.equals(errorCode)
                && !AcfCapabilityErrorCodes.RUNTIME_CONCURRENCY_REJECTED.equals(errorCode)
                && !AcfCapabilityErrorCodes.RUNTIME_RATE_LIMITED.equals(errorCode)
                && !AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_OPEN.equals(errorCode)
                && !AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_HALF_OPEN_LIMITED.equals(errorCode);
    }

    private CircuitPermit permit(Object leaseState) {
        return leaseState instanceof CircuitPermit permit ? permit : null;
    }

    private void cleanupStaleCircuits(long now) {
        if (operationCount.incrementAndGet() % cleanupInterval != 0) {
            return;
        }
        for (String key : circuits.keySet()) {
            circuits.computeIfPresent(key, (ignored, state) -> state.canEvict(now) ? null : state);
        }
    }

    private String key(CapabilityRuntimeGuardContext context) {
        Long tenantId = context.getInvocationContext().getTenantId();
        return String.valueOf(tenantId) + ":" + context.getDefinition().getName();
    }

    private enum CircuitPhase {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final class CircuitState {

        private CircuitPhase phase = CircuitPhase.CLOSED;
        private int failureThreshold;
        private long openDurationMs;
        private int halfOpenMaxCalls;
        private int consecutiveFailures;
        private int halfOpenIssued;
        private int halfOpenInflight;
        private long openUntil;
        private long generation;
        private long lastAccessAt;

        private CapabilityRuntimeGuardResult acquire(CapabilityRuntimePolicy policy, long observedNow) {
            long now = Math.max(observedNow, lastAccessAt);
            ensurePolicy(policy, now);
            lastAccessAt = now;
            if (phase == CircuitPhase.OPEN) {
                if (now < openUntil) {
                    return openRejection(now);
                }
                transitionToHalfOpen();
            }
            if (phase == CircuitPhase.HALF_OPEN) {
                if (halfOpenIssued >= halfOpenMaxCalls) {
                    return CapabilityRuntimeGuardResult.rejected(CODE,
                            AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_HALF_OPEN_LIMITED,
                            "Capability circuit breaker half-open trial limit exceeded", true);
                }
                halfOpenIssued++;
                halfOpenInflight++;
                return CapabilityRuntimeGuardResult.allowed(CODE,
                        new CircuitPermit(generation, true));
            }
            return CapabilityRuntimeGuardResult.allowed(CODE,
                    new CircuitPermit(generation, false));
        }

        private CircuitState releaseHalfOpenPermit(CircuitPermit permit, long observedNow) {
            long now = Math.max(observedNow, lastAccessAt);
            lastAccessAt = now;
            if (phase == CircuitPhase.HALF_OPEN && permit.generation() == generation) {
                halfOpenInflight = Math.max(0, halfOpenInflight - 1);
                halfOpenIssued = Math.max(0, halfOpenIssued - 1);
            }
            return this;
        }

        private CircuitState complete(CircuitPermit permit, boolean recordFailure, long observedNow) {
            long now = Math.max(observedNow, lastAccessAt);
            lastAccessAt = now;
            if (permit.generation() != generation) {
                return this;
            }
            if (permit.halfOpenProbe()) {
                if (phase != CircuitPhase.HALF_OPEN) {
                    return this;
                }
                halfOpenInflight = Math.max(0, halfOpenInflight - 1);
                if (recordFailure) {
                    transitionToOpen(now);
                } else if (halfOpenInflight == 0) {
                    transitionToClosed();
                }
                return this;
            }
            if (phase != CircuitPhase.CLOSED) {
                return this;
            }
            if (!recordFailure) {
                consecutiveFailures = 0;
                return this;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                transitionToOpen(now);
            }
            return this;
        }

        private void ensurePolicy(CapabilityRuntimePolicy policy, long now) {
            int currentFailureThreshold = policy.getCircuitFailureThreshold();
            long currentOpenDurationMs = policy.getCircuitOpenSeconds() * 1_000L;
            int currentHalfOpenMaxCalls = policy.getCircuitHalfOpenMaxCalls();
            if (failureThreshold == currentFailureThreshold
                    && openDurationMs == currentOpenDurationMs
                    && halfOpenMaxCalls == currentHalfOpenMaxCalls) {
                return;
            }
            failureThreshold = currentFailureThreshold;
            openDurationMs = currentOpenDurationMs;
            halfOpenMaxCalls = currentHalfOpenMaxCalls;
            generation++;
            phase = CircuitPhase.CLOSED;
            consecutiveFailures = 0;
            halfOpenIssued = 0;
            halfOpenInflight = 0;
            openUntil = 0;
            lastAccessAt = now;
        }

        private CapabilityRuntimeGuardResult openRejection(long now) {
            long retryAfterMs = Math.max(1, openUntil - now);
            return CapabilityRuntimeGuardResult.rejected(CODE,
                    AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_OPEN,
                    "Capability circuit breaker is open; retry after " + retryAfterMs + " ms", true);
        }

        private void transitionToOpen(long now) {
            generation++;
            phase = CircuitPhase.OPEN;
            openUntil = now + openDurationMs;
            consecutiveFailures = failureThreshold;
            halfOpenIssued = 0;
            halfOpenInflight = 0;
        }

        private void transitionToHalfOpen() {
            generation++;
            phase = CircuitPhase.HALF_OPEN;
            halfOpenIssued = 0;
            halfOpenInflight = 0;
        }

        private void transitionToClosed() {
            generation++;
            phase = CircuitPhase.CLOSED;
            consecutiveFailures = 0;
            halfOpenIssued = 0;
            halfOpenInflight = 0;
            openUntil = 0;
        }

        private boolean canEvict(long now) {
            if (phase == CircuitPhase.HALF_OPEN && halfOpenInflight > 0) {
                return false;
            }
            long retentionMs = Math.max(MIN_IDLE_RETENTION_MS, openDurationMs);
            return now >= lastAccessAt && now - lastAccessAt >= retentionMs;
        }
    }

    private record CircuitPermit(long generation, boolean halfOpenProbe) {
    }

    private static final class CircuitDecision {

        private CapabilityRuntimeGuardResult result;

    }

}
