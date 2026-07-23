package cn.iocoder.yudao.framework.acf.core.policy;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import lombok.Getter;

/**
 * 单个能力治理策略的决策
 *
 * @author bujidao
 */
@Getter
public final class CapabilityPolicyDecision {

    private final String policyCode;
    private final boolean allowed;
    private final String errorCode;
    private final String reason;
    private final CapabilityDefinition effectiveDefinition;

    private CapabilityPolicyDecision(String policyCode, boolean allowed, String errorCode, String reason,
                                     CapabilityDefinition effectiveDefinition) {
        this.policyCode = policyCode;
        this.allowed = allowed;
        this.errorCode = errorCode;
        this.reason = reason;
        this.effectiveDefinition = effectiveDefinition;
    }

    public static CapabilityPolicyDecision allow(String policyCode, String reason,
                                                 CapabilityDefinition effectiveDefinition) {
        return new CapabilityPolicyDecision(policyCode, true, null, reason, effectiveDefinition);
    }

    public static CapabilityPolicyDecision deny(String policyCode, String errorCode, String reason) {
        return new CapabilityPolicyDecision(policyCode, false, errorCode, reason, null);
    }

}
