package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityRuntimePolicyTest {

    @Test
    void shouldCreateConservativeDefaultPolicy() {
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.defaults(5_000);

        assertThat(policy.getTimeoutMs()).isEqualTo(5_000);
        assertThat(policy.getMaxConcurrency()).isNull();
        assertThat(policy.isRateLimitEnabled()).isFalse();
        assertThat(policy.isCircuitBreakerEnabled()).isFalse();
        assertThat(policy.isRetryEnabled()).isFalse();
        assertThat(policy.getRetryMaxAttempts()).isOne();
        assertThat(policy.getRetryBackoffMs()).isZero();
        assertThat(policy.summary()).isEqualTo("timeoutMs=5000");
    }

    @Test
    void shouldBuildCompleteValidatedPolicy() {
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.builder()
                .timeoutMs(8_000)
                .maxConcurrency(12)
                .rateLimitEnabled(true)
                .rateLimitCount(100)
                .rateLimitWindowSeconds(60)
                .circuitBreakerEnabled(true)
                .circuitFailureThreshold(5)
                .circuitOpenSeconds(30)
                .circuitHalfOpenMaxCalls(2)
                .retryEnabled(true)
                .retryMaxAttempts(3)
                .retryBackoffMs(200)
                .build();

        assertThat(policy.summary()).isEqualTo(
                "timeoutMs=8000; maxConcurrency=12; rateLimit=100/60s; "
                        + "circuitBreaker=5/30s/halfOpen=2; retry=3x@200ms");
        assertThat(policy.toBuilder().timeoutMs(9_000).build().getTimeoutMs()).isEqualTo(9_000);
    }

    @Test
    void shouldRejectIncompleteProtectionConfiguration() {
        assertThatThrownBy(() -> CapabilityRuntimePolicy.builder()
                .rateLimitEnabled(true)
                .rateLimitCount(10)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rateLimitWindowSeconds");
        assertThatThrownBy(() -> CapabilityRuntimePolicy.builder()
                .circuitBreakerEnabled(true)
                .circuitFailureThreshold(3)
                .circuitOpenSeconds(10)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("circuitHalfOpenMaxCalls");
        assertThatThrownBy(() -> CapabilityRuntimePolicy.builder()
                .retryEnabled(true)
                .retryMaxAttempts(1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
        assertThatThrownBy(() -> CapabilityRuntimePolicy.builder()
                .rateLimitCount(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rateLimitCount");
    }

    @Test
    void shouldResolveTimeoutFromCapabilityDefinition() {
        CapabilityDefinition definition = CapabilityDefinition.builder()
                .name("test.runtime.policy")
                .timeoutMs(6_000)
                .build();

        CapabilityRuntimePolicy policy = new DefaultCapabilityRuntimePolicyService()
                .resolve(definition, CapabilityContext.empty());

        assertThat(policy.getTimeoutMs()).isEqualTo(6_000);
        assertThat(policy.summary()).isEqualTo("timeoutMs=6000");
    }

}
