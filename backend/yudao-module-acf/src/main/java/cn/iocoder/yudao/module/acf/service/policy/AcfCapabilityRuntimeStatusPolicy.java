package cn.iocoder.yudao.module.acf.service.policy;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicy;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyContext;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyDecision;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyPhase;
import cn.iocoder.yudao.module.acf.dal.dataobject.capability.AcfCapabilityDefinitionDO;
import cn.iocoder.yudao.module.acf.enums.AcfCapabilityRuntimeStatus;
import cn.iocoder.yudao.module.acf.service.capability.AcfCapabilityDefinitionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * ACF 能力运行时状态策略
 *
 * @author bujidao
 */
@Component
public class AcfCapabilityRuntimeStatusPolicy implements CapabilityPolicy {

    public static final String CODE = "ACF_RUNTIME_STATUS";
    public static final String ERROR_CAPABILITY_MISSING = "CAPABILITY_MISSING";

    @Resource
    private AcfCapabilityDefinitionService capabilityDefinitionService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean supports(CapabilityPolicyPhase phase) {
        return phase == CapabilityPolicyPhase.VISIBILITY || phase == CapabilityPolicyPhase.EXECUTION;
    }

    @Override
    public CapabilityPolicyDecision evaluate(CapabilityPolicyContext context) {
        CapabilityDefinition definition = context.definition();
        AcfCapabilityDefinitionDO definitionDO = capabilityDefinitionService.getRuntimeDefinition(
                definition.getName(), definition.getVersion());
        if (definitionDO == null) {
            return CapabilityPolicyDecision.allow(code(), "definition not synchronized", null);
        }
        if (AcfCapabilityRuntimeStatus.MISSING.equals(definitionDO.getRuntimeStatus())) {
            return CapabilityPolicyDecision.deny(code(), ERROR_CAPABILITY_MISSING,
                    "Capability is missing from current runtime");
        }
        return CapabilityPolicyDecision.allow(code(), "runtime status allowed", null);
    }

}
