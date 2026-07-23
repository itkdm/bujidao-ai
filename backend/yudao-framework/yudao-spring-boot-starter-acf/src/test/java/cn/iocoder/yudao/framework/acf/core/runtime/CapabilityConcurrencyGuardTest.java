package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityConcurrencyGuardTest {

    private final CapabilityConcurrencyGuard guard = new CapabilityConcurrencyGuard();

    @Test
    void shouldRejectAtLimitAndAllowAgainAfterRelease() {
        CapabilityRuntimeGuardContext context = context(1L);

        CapabilityRuntimeGuardResult first = guard.acquire(context);
        CapabilityRuntimeGuardResult second = guard.acquire(context);
        guard.release(context);
        CapabilityRuntimeGuardResult third = guard.acquire(context);
        guard.release(context);

        assertThat(first.isAllowed()).isTrue();
        assertThat(second.isAllowed()).isFalse();
        assertThat(second.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_CONCURRENCY_REJECTED);
        assertThat(third.isAllowed()).isTrue();
    }

    @Test
    void shouldIsolateCountersByTenant() {
        CapabilityRuntimeGuardContext tenantOne = context(1L);
        CapabilityRuntimeGuardContext tenantTwo = context(2L);

        CapabilityRuntimeGuardResult tenantOneResult = guard.acquire(tenantOne);
        CapabilityRuntimeGuardResult tenantTwoResult = guard.acquire(tenantTwo);
        guard.release(tenantOne);
        guard.release(tenantTwo);

        assertThat(tenantOneResult.isAllowed()).isTrue();
        assertThat(tenantTwoResult.isAllowed()).isTrue();
    }

    @Test
    void shouldAtomicallyEnforceLimitUnderConcurrentAcquisition() throws Exception {
        int contenderCount = 8;
        CapabilityRuntimeGuardContext context = context(1L);
        ExecutorService executorService = Executors.newFixedThreadPool(contenderCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(contenderCount);
        AtomicInteger allowedCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < contenderCount; index++) {
                futures.add(executorService.submit(() -> {
                    start.await();
                    CapabilityRuntimeGuardResult result = guard.acquire(context);
                    if (result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    }
                    acquired.countDown();
                    assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
                    if (result.isAllowed()) {
                        guard.release(context);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }

        assertThat(allowedCount).hasValue(1);
        assertThat(guard.acquire(context).isAllowed()).isTrue();
        guard.release(context);
    }

    private CapabilityRuntimeGuardContext context(Long tenantId) {
        return new CapabilityRuntimeGuardContext(
                CapabilityDefinition.builder().name("test.runtime.concurrent").timeoutMs(1_000).build(),
                CapabilityContext.builder().tenantId(tenantId).build(),
                CapabilityRuntimePolicy.builder().maxConcurrency(1).build());
    }

}
