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
 * Ordered capability governance policy chain.
 *
 * Policy overlays may tune timeout or strengthen security declarations. They
 * cannot weaken the registered capability baseline or change its public contract.
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
                validateOverlay(policy, effectiveDefinition, decision.getEffectiveDefinition());
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

    private void validateOverlay(CapabilityPolicy policy, CapabilityDefinition current,
                                 CapabilityDefinition effective) {
        requireUnchanged(policy, "name", current.getName(), effective.getName());
        requireUnchanged(policy, "version", current.getVersion(), effective.getVersion());
        requireUnchanged(policy, "title", current.getTitle(), effective.getTitle());
        requireUnchanged(policy, "description", current.getDescription(), effective.getDescription());
        requireUnchanged(policy, "category", current.getCategory(), effective.getCategory());
        requireUnchanged(policy, "argumentType", current.getArgumentType(), effective.getArgumentType());
        requireUnchanged(policy, "returnType", current.getReturnType(), effective.getReturnType());
        requireUnchanged(policy, "permissions", current.getPermissions(), effective.getPermissions());
        requireUnchanged(policy, "permissionMode", current.getPermissionMode(), effective.getPermissionMode());
        requireUnchanged(policy, "inputSchema", current.getInputSchema(), effective.getInputSchema());
        requireUnchanged(policy, "outputSchema", current.getOutputSchema(), effective.getOutputSchema());
        if (effective.getTimeoutMs() <= 0) {
            throw invalidOverlay(policy, "timeoutMs must be greater than zero");
        }
        if (current.getRiskLevel() != null && (effective.getRiskLevel() == null
                || effective.getRiskLevel().ordinal() < current.getRiskLevel().ordinal())) {
            throw invalidOverlay(policy, "riskLevel must not be lowered");
        }
        if (current.isSideEffect() && !effective.isSideEffect()) {
            throw invalidOverlay(policy, "sideEffect must not be disabled");
        }
        if (current.isConfirmationRequired() && !effective.isConfirmationRequired()) {
            throw invalidOverlay(policy, "confirmationRequired must not be disabled");
        }
    }

    private void requireUnchanged(CapabilityPolicy policy, String field, Object current, Object effective) {
        if (!Objects.equals(current, effective)) {
            throw invalidOverlay(policy, field + " must not be changed");
        }
    }

    private IllegalStateException invalidOverlay(CapabilityPolicy policy, String reason) {
        return new IllegalStateException("Capability policy overlay is invalid: " + policy.code() + ", " + reason);
    }

}
