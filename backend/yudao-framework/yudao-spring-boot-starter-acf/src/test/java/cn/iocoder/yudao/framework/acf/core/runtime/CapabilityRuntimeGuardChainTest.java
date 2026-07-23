package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityRuntimeGuardChainTest {

    @Test
    void shouldAcquireInOrderAndCompleteInReverseOrderOnce() {
        List<String> events = new ArrayList<>();
        RecordingGuard late = RecordingGuard.allowed("LATE", 200, events);
        RecordingGuard early = RecordingGuard.allowed("EARLY", 100, events);
        CapabilityRuntimeGuardChain chain = new CapabilityRuntimeGuardChain(List.of(late, early));

        CapabilityRuntimeGuardChain.Lease lease = chain.acquire(context(1L, null));
        lease.onSuccess(CapabilityResult.success("test.runtime.guard", "ok", null));
        lease.release();

        assertThat(lease.isAllowed()).isTrue();
        assertThat(events).containsExactly(
                "acquire:EARLY", "acquire:LATE", "success:LATE", "success:EARLY");
    }

    @Test
    void shouldReleasePreviouslyAcquiredGuardsWhenLaterGuardRejects() {
        List<String> events = new ArrayList<>();
        RecordingGuard acquired = RecordingGuard.allowed("ACQUIRED", 100, events);
        RecordingGuard rejected = RecordingGuard.rejected("REJECTED", 200, events);
        CapabilityRuntimeGuardChain chain = new CapabilityRuntimeGuardChain(List.of(acquired, rejected));

        CapabilityRuntimeGuardChain.Lease lease = chain.acquire(context(1L, null));

        assertThat(lease.isAllowed()).isFalse();
        assertThat(lease.getRejection().getErrorCode()).isEqualTo("TEST_REJECTED");
        assertThat(events).containsExactly("acquire:ACQUIRED", "acquire:REJECTED", "release:ACQUIRED");
    }

    @Test
    void shouldReturnEachGuardLeaseStateToItsCompletionCallback() {
        List<Object> completedStates = new ArrayList<>();
        Object leaseState = new Object();
        CapabilityRuntimeGuard statefulGuard = new CapabilityRuntimeGuard() {
            @Override
            public String code() {
                return "STATEFUL";
            }

            @Override
            public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
                return CapabilityRuntimeGuardResult.allowed(code(), leaseState);
            }

            @Override
            public void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result,
                                  Object currentLeaseState) {
                completedStates.add(currentLeaseState);
            }
        };

        CapabilityRuntimeGuardChain.Lease lease = new CapabilityRuntimeGuardChain(List.of(statefulGuard))
                .acquire(context(1L, null));
        lease.onSuccess(CapabilityResult.success("test.runtime.guard", (Object) "ok"));

        assertThat(completedStates).containsExactly(leaseState);
    }

    @Test
    void shouldConvertGuardExceptionToStableRejection() {
        CapabilityRuntimeGuard failingGuard = new CapabilityRuntimeGuard() {
            @Override
            public String code() {
                return "FAILING";
            }

            @Override
            public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
                throw new IllegalStateException("guard storage unavailable");
            }
        };

        CapabilityRuntimeGuardChain.Lease lease = new CapabilityRuntimeGuardChain(List.of(failingGuard))
                .acquire(context(1L, null));

        assertThat(lease.isAllowed()).isFalse();
        assertThat(lease.getRejection().getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_GUARD_ERROR);
        assertThat(lease.getRejection().getReason()).isEqualTo("guard storage unavailable");
    }

    @Test
    void shouldRejectDuplicateGuardCodes() {
        assertThatThrownBy(() -> new CapabilityRuntimeGuardChain(List.of(
                RecordingGuard.allowed("DUPLICATE", 100, new ArrayList<>()),
                RecordingGuard.allowed("DUPLICATE", 200, new ArrayList<>()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate runtime guard code");
    }

    private CapabilityRuntimeGuardContext context(Long tenantId, Integer maxConcurrency) {
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.builder()
                .maxConcurrency(maxConcurrency)
                .build();
        return new CapabilityRuntimeGuardContext(
                CapabilityDefinition.builder().name("test.runtime.guard").timeoutMs(1_000).build(),
                CapabilityContext.builder().tenantId(tenantId).build(), policy);
    }

    static class RecordingGuard implements CapabilityRuntimeGuard {

        private final String code;
        private final int order;
        private final List<String> events;
        private final boolean allowed;

        private RecordingGuard(String code, int order, List<String> events, boolean allowed) {
            this.code = code;
            this.order = order;
            this.events = events;
            this.allowed = allowed;
        }

        static RecordingGuard allowed(String code, int order, List<String> events) {
            return new RecordingGuard(code, order, events, true);
        }

        static RecordingGuard rejected(String code, int order, List<String> events) {
            return new RecordingGuard(code, order, events, false);
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
            events.add("acquire:" + code);
            return allowed ? CapabilityRuntimeGuardResult.allowed(code)
                    : CapabilityRuntimeGuardResult.rejected(code, "TEST_REJECTED", "rejected", false);
        }

        @Override
        public void release(CapabilityRuntimeGuardContext context) {
            events.add("release:" + code);
        }

        @Override
        public void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result) {
            events.add("success:" + code);
        }
    }

}
