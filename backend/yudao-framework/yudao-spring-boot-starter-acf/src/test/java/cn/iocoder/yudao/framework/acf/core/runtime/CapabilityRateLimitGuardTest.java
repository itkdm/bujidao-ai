package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRateLimitGuardTest {

    private final MutableClock clock = new MutableClock();
    private final CapabilityRateLimitGuard guard = new CapabilityRateLimitGuard(clock, 256);

    @Test
    void shouldRejectAfterSlidingWindowLimitAndAllowAfterExpiration() {
        CapabilityRuntimeGuardContext context = context(1L, "test.runtime.search", 2, 10);

        CapabilityRuntimeGuardResult first = guard.acquire(context);
        CapabilityRuntimeGuardResult second = guard.acquire(context);
        CapabilityRuntimeGuardResult rejected = guard.acquire(context);
        clock.advanceMillis(10_000);
        CapabilityRuntimeGuardResult afterExpiration = guard.acquire(context);

        assertThat(first.isAllowed()).isTrue();
        assertThat(second.isAllowed()).isTrue();
        assertThat(rejected.isAllowed()).isFalse();
        assertThat(rejected.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_RATE_LIMITED);
        assertThat(rejected.getReason()).isEqualTo("Capability rate limit exceeded; retry after 10000 ms");
        assertThat(rejected.isRetryable()).isTrue();
        assertThat(afterExpiration.isAllowed()).isTrue();
    }

    @Test
    void shouldIsolateWindowsByTenantAndCapability() {
        CapabilityRuntimeGuardContext tenantOne = context(1L, "test.runtime.search", 1, 60);
        CapabilityRuntimeGuardContext tenantTwo = context(2L, "test.runtime.search", 1, 60);
        CapabilityRuntimeGuardContext anotherCapability = context(1L, "test.runtime.detail", 1, 60);

        assertThat(guard.acquire(tenantOne).isAllowed()).isTrue();
        assertThat(guard.acquire(tenantOne).isAllowed()).isFalse();
        assertThat(guard.acquire(tenantTwo).isAllowed()).isTrue();
        assertThat(guard.acquire(anotherCapability).isAllowed()).isTrue();
    }

    @Test
    void shouldStartFreshWindowWhenPolicyChanges() {
        CapabilityRuntimeGuardContext strictPolicy = context(1L, "test.runtime.search", 1, 60);
        CapabilityRuntimeGuardContext relaxedPolicy = context(1L, "test.runtime.search", 2, 60);

        assertThat(guard.acquire(strictPolicy).isAllowed()).isTrue();
        assertThat(guard.acquire(strictPolicy).isAllowed()).isFalse();
        assertThat(guard.acquire(relaxedPolicy).isAllowed()).isTrue();
        assertThat(guard.acquire(relaxedPolicy).isAllowed()).isTrue();
    }

    @Test
    void shouldCleanInactiveWindowsWithoutScheduler() {
        CapabilityRateLimitGuard cleanupGuard = new CapabilityRateLimitGuard(clock, 1);

        cleanupGuard.acquire(context(1L, "test.runtime.search", 1, 1));
        clock.advanceMillis(1_000);
        cleanupGuard.acquire(context(2L, "test.runtime.search", 1, 1));

        assertThat(cleanupGuard.trackedWindowCount()).isOne();
    }

    @Test
    void shouldAtomicallyEnforceLimitUnderConcurrentAcquisition() throws Exception {
        int contenderCount = 12;
        int limit = 3;
        CapabilityRuntimeGuardContext context = context(1L, "test.runtime.search", limit, 60);
        ExecutorService executorService = Executors.newFixedThreadPool(contenderCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < contenderCount; index++) {
                futures.add(executorService.submit(() -> {
                    start.await();
                    if (guard.acquire(context).isAllowed()) {
                        allowedCount.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }

        assertThat(allowedCount).hasValue(limit);
    }

    private CapabilityRuntimeGuardContext context(Long tenantId, String capabilityName,
                                                  int limit, int windowSeconds) {
        return new CapabilityRuntimeGuardContext(
                CapabilityDefinition.builder().name(capabilityName).timeoutMs(1_000).build(),
                CapabilityContext.builder().tenantId(tenantId).build(),
                CapabilityRuntimePolicy.builder()
                        .rateLimitEnabled(true)
                        .rateLimitCount(limit)
                        .rateLimitWindowSeconds(windowSeconds)
                        .build());
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
