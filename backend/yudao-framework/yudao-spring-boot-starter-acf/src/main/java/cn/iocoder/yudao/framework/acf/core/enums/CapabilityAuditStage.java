package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力执行审计阶段
 *
 * @author bujidao
 */
public enum CapabilityAuditStage {

    REQUEST_VALIDATION,

    CAPABILITY_LOOKUP,

    GOVERNANCE,

    ARGUMENT_VALIDATION,

    CONFIRMATION,

    IDEMPOTENCY,

    RUNTIME_POLICY,

    RUNTIME_GUARD,

    INVOCATION,

    COMPLETED

}
