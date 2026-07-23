package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyDecision;
import lombok.Getter;

import java.util.List;

/**
 * 能力治理策略链的执行结果
 *
 * @author bujidao
 */
@Getter
public final class CapabilityPolicyResult {

    private final boolean allowed;
    private final CapabilityDefinition definition;
    private final String errorCode;
    private final String reason;
    private final List<CapabilityPolicyDecision> decisions;

    private CapabilityPolicyResult(boolean allowed, CapabilityDefinition definition, String errorCode,
                                   String reason, List<CapabilityPolicyDecision> decisions) {
        this.allowed = allowed;
        this.definition = definition;
        this.errorCode = errorCode;
        this.reason = reason;
        this.decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    public static CapabilityPolicyResult allow(CapabilityDefinition definition,
                                               List<CapabilityPolicyDecision> decisions) {
        return new CapabilityPolicyResult(true, definition, null, null, decisions);
    }

    public static CapabilityPolicyResult deny(CapabilityDefinition definition, String errorCode, String reason,
                                              List<CapabilityPolicyDecision> decisions) {
        return new CapabilityPolicyResult(false, definition, errorCode, reason, decisions);
    }

}
