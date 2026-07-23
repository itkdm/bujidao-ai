package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCircuitBreakerGuardTest {

    private static final String CAPABILITY_NAME = "test.runtime.circuit";

    private final MutableClock clock = new MutableClock();
    private final CapabilityCircuitBreakerGuard guard = new CapabilityCircuitBreakerGuard(clock, 256);

    @Test
    void shouldOpenAfterConsecutiveSystemFailuresAndExposeRetryDelay() {
        CapabilityRuntimeGuardContext context = context(1L, CAPABILITY_NAME, policy(2, 10, 1));

        fail(context, acquireAllowed(context));
        fail(context, acquireAllowed(context));
        CapabilityRuntimeGuardResult rejected = guard.acquire(context);

        assertThat(rejected.isAllowed()).isFalse();
        assertThat(rejected.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_OPEN);
        assertThat(rejected.getReason()).isEqualTo("Capability circuit breaker is open; retry after 10000 ms");
        assertThat(rejected.isRetryable()).isTrue();
    }

    @Test
    void shouldResetConsecutiveFailureCountAfterSuccess() {
        CapabilityRuntimeGuardContext context = context(1L, CAPABILITY_NAME, policy(2, 10, 1));

        fail(context, acquireAllowed(context));
        succeed(context, acquireAllowed(context));
        fail(context, acquireAllowed(context));

        assertThat(guard.acquire(context).isAllowed()).isTrue();
    }

    @Test
    void shouldLimitHalfOpenProbeBatchAndCloseAfterAllAdmittedProbesSucceed() {
        CapabilityRuntimeGuardContext context = context(1L, CAPABILITY_NAME, policy(1, 1, 2));
        fail(context, acquireAllowed(context));
        clock.advanceMillis(1_000);

        CapabilityRuntimeGuardResult firstProbe = acquireAllowed(context);
        CapabilityRuntimeGuardResult secondProbe = acquireAllowed(context);
        CapabilityRuntimeGuardResult rejected = guard.acquire(context);
        succeed(context, firstProbe);

        assertThat(rejected.getErrorCode())
                .isEqualTo(AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_HALF_OPEN_LIMITED);
        assertThat(guard.acquire(context).isAllowed()).isFalse();

        succeed(context, secondProbe);

        assertThat(guard.acquire(context).isAllowed()).isTrue();
    }

    @Test
    void shouldReopenOnHalfOpenFailureAndIgnoreStaleProbeCompletion() {
        CapabilityRuntimeGuardContext context = context(1L, CAPABILITY_NAME, policy(1, 1, 2));
        fail(context, acquireAllowed(context));
        clock.advanceMillis(1_000);
        CapabilityRuntimeGuardResult failingProbe = acquireAllowed(context);
        CapabilityRuntimeGuardResult staleProbe = acquireAllowed(context);

        fail(context, failingProbe);
        succeed(context, staleProbe);

        CapabilityRuntimeGuardResult rejected = guard.acquire(context);
        assertThat(rejected.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_OPEN);
        assertThat(rejected.getReason()).contains("retry after 1000 ms");
    }

    @Test
    void shouldReturnUnusedHalfOpenPermitWhenLaterGuardRejects() {
        CapabilityRuntimeGuardContext context = context(1L, CAPABILITY_NAME, policy(1, 1, 1));
        fail(context, acquireAllowed(context));
        clock.advanceMillis(1_000);
        CapabilityRuntimeGuardResult unusedProbe = acquireAllowed(context);

        guard.release(context, unusedProbe.getLeaseState());

        assertThat(guard.acquire(context).isAllowed()).isTrue();
    }

    @Test
    void shouldIgnoreBusinessFailureWithoutSystemCause() {
        CapabilityRuntimeGuardContext context = context(1L, CAPABILITY_NAME, policy(1, 10, 1));
        CapabilityRuntimeGuardResult permit = acquireAllowed(context);

        guard.onFailure(context,
                CapabilityResult.failure(CAPABILITY_NAME, "BUSINESS_REJECTED", "business rejected"),
                null, permit.getLeaseState());

        assertThat(guard.acquire(context).isAllowed()).isTrue();
    }

    @Test
    void shouldResetStateWhenCircuitPolicyChanges() {
        CapabilityRuntimeGuardContext strictContext = context(1L, CAPABILITY_NAME, policy(1, 10, 1));
        fail(strictContext, acquireAllowed(strictContext));
        assertThat(guard.acquire(strictContext).isAllowed()).isFalse();

        CapabilityRuntimeGuardContext changedContext = context(1L, CAPABILITY_NAME, policy(2, 10, 1));

        assertThat(guard.acquire(changedContext).isAllowed()).isTrue();
    }

    @Test
    void shouldIsolateCircuitStateByTenantAndCapability() {
        CapabilityRuntimePolicy policy = policy(1, 10, 1);
        CapabilityRuntimeGuardContext tenantOne = context(1L, CAPABILITY_NAME, policy);
        CapabilityRuntimeGuardContext tenantTwo = context(2L, CAPABILITY_NAME, policy);
        CapabilityRuntimeGuardContext anotherCapability = context(1L, "test.runtime.other", policy);

        fail(tenantOne, acquireAllowed(tenantOne));

        assertThat(guard.acquire(tenantOne).isAllowed()).isFalse();
        assertThat(guard.acquire(tenantTwo).isAllowed()).isTrue();
        assertThat(guard.acquire(anotherCapability).isAllowed()).isTrue();
    }

    @Test
    void shouldCleanInactiveClosedCircuitsWithoutScheduler() {
        CapabilityCircuitBreakerGuard cleanupGuard = new CapabilityCircuitBreakerGuard(clock, 1);
        CapabilityRuntimeGuardContext first = context(1L, CAPABILITY_NAME, policy(1, 1, 1));
        CapabilityRuntimeGuardResult firstPermit = cleanupGuard.acquire(first);
        cleanupGuard.onSuccess(first, CapabilityResult.success(CAPABILITY_NAME, (Object) "ok"),
                firstPermit.getLeaseState());
        clock.advanceMillis(60_000);

        cleanupGuard.acquire(context(2L, CAPABILITY_NAME, policy(1, 1, 1)));

        assertThat(cleanupGuard.trackedCircuitCount()).isOne();
    }

    private CapabilityRuntimeGuardResult acquireAllowed(CapabilityRuntimeGuardContext context) {
        CapabilityRuntimeGuardResult result = guard.acquire(context);
        assertThat(result.isAllowed()).isTrue();
        return result;
    }

    private void succeed(CapabilityRuntimeGuardContext context, CapabilityRuntimeGuardResult permit) {
        guard.onSuccess(context, CapabilityResult.success(context.getDefinition().getName(), (Object) "ok"),
                permit.getLeaseState());
    }

    private void fail(CapabilityRuntimeGuardContext context, CapabilityRuntimeGuardResult permit) {
        guard.onFailure(context,
                CapabilityResult.failure(context.getDefinition().getName(),
                        AcfCapabilityErrorCodes.INVOKE_ERROR, "boom", true),
                new IllegalStateException("boom"), permit.getLeaseState());
    }

    private CapabilityRuntimePolicy policy(int failureThreshold, int openSeconds, int halfOpenMaxCalls) {
        return CapabilityRuntimePolicy.builder()
                .circuitBreakerEnabled(true)
                .circuitFailureThreshold(failureThreshold)
                .circuitOpenSeconds(openSeconds)
                .circuitHalfOpenMaxCalls(halfOpenMaxCalls)
                .build();
    }

    private CapabilityRuntimeGuardContext context(Long tenantId, String capabilityName,
                                                  CapabilityRuntimePolicy policy) {
        return new CapabilityRuntimeGuardContext(
                CapabilityDefinition.builder().name(capabilityName).timeoutMs(1_000).build(),
                CapabilityContext.builder().tenantId(tenantId).build(), policy);
    }

    private static final class MutableClock extends Clock {

        private long currentTimeMillis = 1_000_000L;

        void advanceMillis(long millis) {
            currentTimeMillis += millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(currentTimeMillis);
        }

        @Override
        public long millis() {
            return currentTimeMillis;
        }
    }

}
