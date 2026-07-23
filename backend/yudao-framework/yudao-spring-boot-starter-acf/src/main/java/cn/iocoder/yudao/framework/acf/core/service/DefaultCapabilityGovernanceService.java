package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyChain;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyPhase;
import lombok.RequiredArgsConstructor;

/**
 * 基于策略链的默认能力治理服务
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class DefaultCapabilityGovernanceService implements CapabilityGovernanceService {

    private final CapabilityPolicyChain policyChain;

    @Override
    public CapabilityPolicyResult evaluate(CapabilityPolicyPhase phase, CapabilityDefinition definition,
                                           CapabilityContext context) {
        return policyChain.evaluate(phase, definition, context);
    }

}
