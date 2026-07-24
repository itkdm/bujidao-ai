package cn.iocoder.yudao.framework.acf.core.policy;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityPolicyChainTest {

    @Test
    void shouldEvaluatePoliciesInOrderAndPropagateEffectiveDefinition() {
        List<String> executedPolicies = new ArrayList<>();
        List<Integer> observedTimeouts = new ArrayList<>();
        CapabilityPolicy laterPolicy = policy("LATER", 200, phase -> true, context -> {
            executedPolicies.add("LATER");
            observedTimeouts.add(context.definition().getTimeoutMs());
            return CapabilityPolicyDecision.allow("LATER", "later allowed", null);
        });
        CapabilityPolicy earlierPolicy = policy("EARLIER", 100, phase -> true, context -> {
            executedPolicies.add("EARLIER");
            CapabilityDefinition effectiveDefinition = context.definition().toBuilder().timeoutMs(5_000).build();
            return CapabilityPolicyDecision.allow("EARLIER", "timeout overlaid", effectiveDefinition);
        });
        CapabilityContext invocationContext = CapabilityContext.builder()
                .userId(10L)
                .consumerType(CapabilityConsumerType.AGENT)
                .build();

        CapabilityPolicyResult result = new CapabilityPolicyChain(List.of(laterPolicy, earlierPolicy))
                .evaluate(CapabilityPolicyPhase.EXECUTION, definition(), invocationContext);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getDefinition().getTimeoutMs()).isEqualTo(5_000);
        assertThat(result.getDecisions()).extracting(CapabilityPolicyDecision::getPolicyCode)
                .containsExactly("EARLIER", "LATER");
        assertThat(executedPolicies).containsExactly("EARLIER", "LATER");
        assertThat(observedTimeouts).containsExactly(5_000);
    }

    @Test
    void shouldStopAtFirstDeniedPolicy() {
        List<String> executedPolicies = new ArrayList<>();
        CapabilityPolicy allowPolicy = policy("ALLOW", 100, phase -> true, context -> {
            executedPolicies.add("ALLOW");
            return CapabilityPolicyDecision.allow("ALLOW", "allowed", null);
        });
        CapabilityPolicy denyPolicy = policy("DENY", 200, phase -> true, context -> {
            executedPolicies.add("DENY");
            return CapabilityPolicyDecision.deny("DENY", "CAPABILITY_DISABLED", "capability disabled");
        });
        CapabilityPolicy skippedPolicy = policy("SKIPPED", 300, phase -> true, context -> {
            executedPolicies.add("SKIPPED");
            return CapabilityPolicyDecision.allow("SKIPPED", "allowed", null);
        });

        CapabilityPolicyResult result = new CapabilityPolicyChain(List.of(skippedPolicy, denyPolicy, allowPolicy))
                .evaluate(CapabilityPolicyPhase.EXECUTION, definition(), CapabilityContext.empty());

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("CAPABILITY_DISABLED");
        assertThat(result.getReason()).isEqualTo("capability disabled");
        assertThat(result.getDecisions()).extracting(CapabilityPolicyDecision::getPolicyCode)
                .containsExactly("ALLOW", "DENY");
        assertThat(executedPolicies).containsExactly("ALLOW", "DENY");
        assertThatThrownBy(() -> result.getDecisions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldOnlyEvaluatePoliciesSupportingCurrentPhase() {
        CapabilityPolicy executionPolicy = policy("EXECUTION_ONLY", 100,
                phase -> phase == CapabilityPolicyPhase.EXECUTION,
                context -> CapabilityPolicyDecision.deny("EXECUTION_ONLY", "DENIED", "denied"));
        CapabilityPolicyChain chain = new CapabilityPolicyChain(List.of(executionPolicy));

        CapabilityPolicyResult visibilityResult = chain.evaluate(CapabilityPolicyPhase.VISIBILITY,
                definition(), CapabilityContext.empty());
        CapabilityPolicyResult executionResult = chain.evaluate(CapabilityPolicyPhase.EXECUTION,
                definition(), CapabilityContext.empty());

        assertThat(visibilityResult.isAllowed()).isTrue();
        assertThat(visibilityResult.getDecisions()).isEmpty();
        assertThat(executionResult.isAllowed()).isFalse();
    }

    @Test
    void shouldRejectDuplicatePolicyCodes() {
        CapabilityPolicy first = policy("DUPLICATE", 100, phase -> true,
                context -> CapabilityPolicyDecision.allow("DUPLICATE", "allowed", null));
        CapabilityPolicy second = policy("DUPLICATE", 200, phase -> true,
                context -> CapabilityPolicyDecision.allow("DUPLICATE", "allowed", null));

        assertThatThrownBy(() -> new CapabilityPolicyChain(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate capability policy code");
    }

    @Test
    void shouldRejectPolicyChangingInvocationContract() {
        CapabilityPolicy invalidPolicy = policy("INVALID_OVERLAY", 100, phase -> true, context -> {
            CapabilityDefinition invalidDefinition = context.definition().toBuilder()
                    .argumentType(Integer.class)
                    .build();
            return CapabilityPolicyDecision.allow("INVALID_OVERLAY", "invalid overlay", invalidDefinition);
        });

        assertThatThrownBy(() -> new CapabilityPolicyChain(List.of(invalidPolicy))
                .evaluate(CapabilityPolicyPhase.EXECUTION, definition(), CapabilityContext.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("argumentType must not be changed");
    }

    @Test
    void shouldAllowPolicyToStrengthenSafetyControls() {
        CapabilityPolicy strengtheningPolicy = policy("STRENGTHEN", 100, phase -> true, context ->
                CapabilityPolicyDecision.allow("STRENGTHEN", "safety strengthened",
                        context.definition().toBuilder()
                                .timeoutMs(5_000)
                                .riskLevel(CapabilityRiskLevel.HIGH)
                                .sideEffect(true)
                                .confirmationRequired(true)
                                .build()));

        CapabilityPolicyResult result = new CapabilityPolicyChain(List.of(strengtheningPolicy))
                .evaluate(CapabilityPolicyPhase.EXECUTION, definition(), CapabilityContext.empty());

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getDefinition().getTimeoutMs()).isEqualTo(5_000);
        assertThat(result.getDefinition().getRiskLevel()).isEqualTo(CapabilityRiskLevel.HIGH);
        assertThat(result.getDefinition().isSideEffect()).isTrue();
        assertThat(result.getDefinition().isConfirmationRequired()).isTrue();
    }

    @Test
    void shouldRejectPolicyThatWeakensSafetyOrChangesPermissions() {
        CapabilityDefinition protectedDefinition = definition().toBuilder()
                .permissions(List.of("order:write"))
                .riskLevel(CapabilityRiskLevel.HIGH)
                .sideEffect(true)
                .confirmationRequired(true)
                .build();
        CapabilityPolicy weakeningPolicy = policy("WEAKEN", 100, phase -> true, context ->
                CapabilityPolicyDecision.allow("WEAKEN", "unsafe",
                        context.definition().toBuilder()
                                .permissions(List.of())
                                .riskLevel(CapabilityRiskLevel.LOW)
                                .sideEffect(false)
                                .confirmationRequired(false)
                                .build()));

        assertThatThrownBy(() -> new CapabilityPolicyChain(List.of(weakeningPolicy))
                .evaluate(CapabilityPolicyPhase.EXECUTION, protectedDefinition, CapabilityContext.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissions must not be changed");
    }

    private CapabilityDefinition definition() {
        return CapabilityDefinition.builder()
                .name("test.product.search")
                .title("搜索商品")
                .description("按关键词搜索商品")
                .timeoutMs(30_000)
                .riskLevel(CapabilityRiskLevel.LOW)
                .argumentType(String.class)
                .returnType(String.class)
                .build();
    }

    private CapabilityPolicy policy(String code, int order, Predicate<CapabilityPolicyPhase> supports,
                                    Function<CapabilityPolicyContext, CapabilityPolicyDecision> evaluator) {
        return new CapabilityPolicy() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public boolean supports(CapabilityPolicyPhase phase) {
                return supports.test(phase);
            }

            @Override
            public CapabilityPolicyDecision evaluate(CapabilityPolicyContext context) {
                return evaluator.apply(context);
            }
        };
    }

}
