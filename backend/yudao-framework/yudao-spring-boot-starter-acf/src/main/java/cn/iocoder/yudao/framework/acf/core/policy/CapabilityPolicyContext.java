package cn.iocoder.yudao.framework.acf.core.policy;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;

/**
 * 单个治理策略的执行上下文
 *
 * @author bujidao
 */
public record CapabilityPolicyContext(CapabilityPolicyPhase phase, CapabilityDefinition definition,
                                      CapabilityContext invocationContext) {
}
