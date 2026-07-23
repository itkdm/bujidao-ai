package cn.iocoder.yudao.framework.acf.core.policy;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 按顺序执行能力治理策略，并在首个拒绝决策处终止
 *
 * @author bujidao
 */
public class CapabilityPolicyChain {

    private final List<CapabilityPolicy> policies;

    public CapabilityPolicyChain(List<CapabilityPolicy> policies) {
        List<CapabilityPolicy> candidates = policies == null ? List.of() : policies;
        validatePolicies(candidates);
        this.policies = candidates.stream()
                .sorted(Comparator.comparingInt(CapabilityPolicy::order).thenComparing(CapabilityPolicy::code))
                .toList();
    }

    public CapabilityPolicyResult evaluate(CapabilityPolicyPhase phase, CapabilityDefinition definition,
                                           CapabilityContext invocationContext) {
        Objects.requireNonNull(phase, "Capability policy phase must not be null");
        if (definition == null) {
            return CapabilityPolicyResult.deny(null, "CAPABILITY_NOT_FOUND", "Capability not found", List.of());
        }

        CapabilityDefinition effectiveDefinition = definition;
        List<CapabilityPolicyDecision> decisions = new ArrayList<>();
        for (CapabilityPolicy policy : policies) {
            if (!policy.supports(phase)) {
                continue;
            }
            CapabilityPolicyDecision decision = Objects.requireNonNull(policy.evaluate(new CapabilityPolicyContext(
                    phase, effectiveDefinition, invocationContext)),
                    "Capability policy decision must not be null: " + policy.code());
            validateDecision(policy, decision);
            decisions.add(decision);
            if (!decision.isAllowed()) {
                return CapabilityPolicyResult.deny(effectiveDefinition, decision.getErrorCode(),
                        decision.getReason(), decisions);
            }
            if (decision.getEffectiveDefinition() != null) {
                validateInvocationContract(policy, effectiveDefinition, decision.getEffectiveDefinition());
                effectiveDefinition = decision.getEffectiveDefinition();
            }
        }
        return CapabilityPolicyResult.allow(effectiveDefinition, decisions);
    }

    private void validatePolicies(List<CapabilityPolicy> candidates) {
        Set<String> codes = new HashSet<>();
        for (CapabilityPolicy policy : candidates) {
            Objects.requireNonNull(policy, "Capability policy must not be null");
            if (!StringUtils.hasText(policy.code())) {
                throw new IllegalArgumentException("Capability policy code must not be blank");
            }
            if (!codes.add(policy.code())) {
                throw new IllegalArgumentException("Duplicate capability policy code: " + policy.code());
            }
        }
    }

    private void validateDecision(CapabilityPolicy policy, CapabilityPolicyDecision decision) {
        if (!policy.code().equals(decision.getPolicyCode())) {
            throw new IllegalStateException("Capability policy decision code mismatch: expected " + policy.code()
                    + " but was " + decision.getPolicyCode());
        }
        if (!decision.isAllowed() && !StringUtils.hasText(decision.getErrorCode())) {
            throw new IllegalStateException("Denied capability policy decision requires error code: "
                    + policy.code());
        }
    }

    private void validateInvocationContract(CapabilityPolicy policy, CapabilityDefinition currentDefinition,
                                            CapabilityDefinition effectiveDefinition) {
        if (!Objects.equals(currentDefinition.getName(), effectiveDefinition.getName())
                || !Objects.equals(currentDefinition.getArgumentType(), effectiveDefinition.getArgumentType())
                || !Objects.equals(currentDefinition.getReturnType(), effectiveDefinition.getReturnType())) {
            throw new IllegalStateException("Capability policy must not change invocation contract: " + policy.code());
        }
    }

}
