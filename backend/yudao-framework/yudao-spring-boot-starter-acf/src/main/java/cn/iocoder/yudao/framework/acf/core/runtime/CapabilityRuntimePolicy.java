package cn.iocoder.yudao.framework.acf.core.runtime;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力执行运行时策略
 *
 * 策略对象在创建时完成归一化和组合校验，后续执行器与保护器只消费有效值，
 * 不再分别处理 null、负数或缺失阈值。
 *
 * @author bujidao
 */
@Getter
public final class CapabilityRuntimePolicy {

    public static final int DEFAULT_TIMEOUT_MS = 30_000;

    private final int timeoutMs;
    private final Integer maxConcurrency;
    private final boolean rateLimitEnabled;
    private final Integer rateLimitCount;
    private final Integer rateLimitWindowSeconds;
    private final boolean circuitBreakerEnabled;
    private final Integer circuitFailureThreshold;
    private final Integer circuitOpenSeconds;
    private final Integer circuitHalfOpenMaxCalls;
    private final boolean retryEnabled;
    private final int retryMaxAttempts;
    private final int retryBackoffMs;

    @Builder(toBuilder = true)
    private CapabilityRuntimePolicy(Integer timeoutMs, Integer maxConcurrency,
                                    Boolean rateLimitEnabled, Integer rateLimitCount,
                                    Integer rateLimitWindowSeconds, Boolean circuitBreakerEnabled,
                                    Integer circuitFailureThreshold, Integer circuitOpenSeconds,
                                    Integer circuitHalfOpenMaxCalls, Boolean retryEnabled,
                                    Integer retryMaxAttempts, Integer retryBackoffMs) {
        this.timeoutMs = timeoutMs == null ? DEFAULT_TIMEOUT_MS : requirePositive(timeoutMs, "timeoutMs");
        this.maxConcurrency = optionalPositive(maxConcurrency, "maxConcurrency");
        this.rateLimitEnabled = Boolean.TRUE.equals(rateLimitEnabled);
        this.rateLimitCount = optionalPositive(rateLimitCount, "rateLimitCount");
        this.rateLimitWindowSeconds = optionalPositive(rateLimitWindowSeconds, "rateLimitWindowSeconds");
        this.circuitBreakerEnabled = Boolean.TRUE.equals(circuitBreakerEnabled);
        this.circuitFailureThreshold = optionalPositive(circuitFailureThreshold, "circuitFailureThreshold");
        this.circuitOpenSeconds = optionalPositive(circuitOpenSeconds, "circuitOpenSeconds");
        this.circuitHalfOpenMaxCalls = optionalPositive(circuitHalfOpenMaxCalls, "circuitHalfOpenMaxCalls");
        this.retryEnabled = Boolean.TRUE.equals(retryEnabled);
        this.retryMaxAttempts = retryMaxAttempts == null ? 1 : requirePositive(retryMaxAttempts, "retryMaxAttempts");
        this.retryBackoffMs = retryBackoffMs == null ? 0 : requireNonNegative(retryBackoffMs, "retryBackoffMs");
        validateRateLimit();
        validateCircuitBreaker();
        validateRetry();
    }

    public static CapabilityRuntimePolicy defaults(int timeoutMs) {
        return CapabilityRuntimePolicy.builder().timeoutMs(timeoutMs).build();
    }

    public String summary() {
        List<String> parts = new ArrayList<>();
        parts.add("timeoutMs=" + timeoutMs);
        if (maxConcurrency != null) {
            parts.add("maxConcurrency=" + maxConcurrency);
        }
        if (rateLimitEnabled) {
            parts.add("rateLimit=" + rateLimitCount + "/" + rateLimitWindowSeconds + "s");
        }
        if (circuitBreakerEnabled) {
            parts.add("circuitBreaker=" + circuitFailureThreshold + "/" + circuitOpenSeconds
                    + "s/halfOpen=" + circuitHalfOpenMaxCalls);
        }
        if (retryEnabled) {
            parts.add("retry=" + retryMaxAttempts + "x@" + retryBackoffMs + "ms");
        }
        return String.join("; ", parts);
    }

    private void validateRateLimit() {
        if (!rateLimitEnabled) {
            return;
        }
        requirePositive(rateLimitCount, "rateLimitCount");
        requirePositive(rateLimitWindowSeconds, "rateLimitWindowSeconds");
    }

    private void validateCircuitBreaker() {
        if (!circuitBreakerEnabled) {
            return;
        }
        requirePositive(circuitFailureThreshold, "circuitFailureThreshold");
        requirePositive(circuitOpenSeconds, "circuitOpenSeconds");
        requirePositive(circuitHalfOpenMaxCalls, "circuitHalfOpenMaxCalls");
    }

    private void validateRetry() {
        if (retryEnabled && retryMaxAttempts < 2) {
            throw new IllegalArgumentException("retryMaxAttempts must be at least 2 when retry is enabled");
        }
    }

    private static Integer optionalPositive(Integer value, String fieldName) {
        return value == null ? null : requirePositive(value, fieldName);
    }

    private static int requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }

    private static int requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

}
