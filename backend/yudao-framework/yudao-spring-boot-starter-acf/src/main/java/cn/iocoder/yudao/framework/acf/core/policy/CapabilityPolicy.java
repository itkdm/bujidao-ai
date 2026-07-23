package cn.iocoder.yudao.framework.acf.core.policy;

/**
 * 能力治理策略扩展点
 *
 * @author bujidao
 */
public interface CapabilityPolicy {

    String code();

    int order();

    default boolean supports(CapabilityPolicyPhase phase) {
        return true;
    }

    CapabilityPolicyDecision evaluate(CapabilityPolicyContext context);

}
