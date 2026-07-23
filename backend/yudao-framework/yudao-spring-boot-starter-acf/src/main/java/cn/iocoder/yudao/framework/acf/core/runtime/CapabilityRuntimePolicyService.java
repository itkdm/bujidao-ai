package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;

/**
 * 能力运行时策略解析服务
 *
 * 业务模块可以根据租户、调用来源或能力配置返回差异化策略。
 *
 * @author bujidao
 */
@FunctionalInterface
public interface CapabilityRuntimePolicyService {

    CapabilityRuntimePolicy resolve(CapabilityDefinition definition, CapabilityContext context);

}
