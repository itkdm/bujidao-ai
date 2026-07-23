package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;

import java.util.Objects;

/**
 * 默认能力运行时策略解析服务
 *
 * @author bujidao
 */
public class DefaultCapabilityRuntimePolicyService implements CapabilityRuntimePolicyService {

    @Override
    public CapabilityRuntimePolicy resolve(CapabilityDefinition definition, CapabilityContext context) {
        Objects.requireNonNull(definition, "definition");
        return CapabilityRuntimePolicy.defaults(definition.getTimeoutMs());
    }

}
