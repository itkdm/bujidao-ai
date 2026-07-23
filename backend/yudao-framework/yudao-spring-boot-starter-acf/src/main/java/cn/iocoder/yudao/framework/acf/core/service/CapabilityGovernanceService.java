package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyPhase;

/**
 * 能力治理统一入口
 *
 * @author bujidao
 */
public interface CapabilityGovernanceService {

    CapabilityPolicyResult evaluate(CapabilityPolicyPhase phase, CapabilityDefinition definition,
                                    CapabilityContext context);

    default CapabilityPolicyResult evaluateExecution(CapabilityDefinition definition, CapabilityContext context) {
        return evaluate(CapabilityPolicyPhase.EXECUTION, definition, context);
    }

    default CapabilityPolicyResult evaluateVisibility(CapabilityDefinition definition, CapabilityContext context) {
        return evaluate(CapabilityPolicyPhase.VISIBILITY, definition, context);
    }

    default CapabilityPolicyResult evaluateReleaseReadiness(CapabilityDefinition definition,
                                                            CapabilityContext context) {
        return evaluate(CapabilityPolicyPhase.RELEASE_READINESS, definition, context);
    }

}
