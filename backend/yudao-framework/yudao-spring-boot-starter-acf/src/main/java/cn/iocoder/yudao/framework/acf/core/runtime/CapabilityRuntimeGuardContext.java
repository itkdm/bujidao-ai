package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;

import java.util.Objects;

/**
 * 运行时保护器上下文
 *
 * @author bujidao
 */
public final class CapabilityRuntimeGuardContext {

    private final CapabilityDefinition definition;
    private final CapabilityContext invocationContext;
    private final CapabilityRuntimePolicy policy;

    public CapabilityRuntimeGuardContext(CapabilityDefinition definition, CapabilityContext invocationContext,
                                         CapabilityRuntimePolicy policy) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.invocationContext = Objects.requireNonNull(invocationContext, "invocationContext");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public CapabilityDefinition getDefinition() {
        return definition;
    }

    public CapabilityContext getInvocationContext() {
        return invocationContext;
    }

    public CapabilityRuntimePolicy getPolicy() {
        return policy;
    }

}
