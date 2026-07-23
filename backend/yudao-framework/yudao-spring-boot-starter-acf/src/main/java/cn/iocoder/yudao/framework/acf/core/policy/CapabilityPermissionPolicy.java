package cn.iocoder.yudao.framework.acf.core.policy;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityPermissionEvaluator;
import lombok.RequiredArgsConstructor;

/**
 * 能力执行权限策略
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityPermissionPolicy implements CapabilityPolicy {

    public static final String CODE = "PERMISSION";
    public static final int ORDER = 500;
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";

    private final CapabilityPermissionEvaluator permissionEvaluator;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean supports(CapabilityPolicyPhase phase) {
        return phase == CapabilityPolicyPhase.EXECUTION || phase == CapabilityPolicyPhase.VISIBILITY;
    }

    @Override
    public CapabilityPolicyDecision evaluate(CapabilityPolicyContext context) {
        CapabilityContext invocationContext = context.invocationContext();
        Long userId = invocationContext == null ? null : invocationContext.getUserId();
        if (userId == null) {
            return CapabilityPolicyDecision.deny(code(), ERROR_PERMISSION_DENIED,
                    "Authenticated user is required");
        }
        if (!permissionEvaluator.hasPermission(userId, context.definition())) {
            return CapabilityPolicyDecision.deny(code(), ERROR_PERMISSION_DENIED,
                    "No permission for capability");
        }
        return CapabilityPolicyDecision.allow(code(), "permission allowed", null);
    }

}
