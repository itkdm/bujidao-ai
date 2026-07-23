package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStepStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 能力执行关键步骤审计记录
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityAuditStepRecord {

    private final String traceId;
    private final int stepNo;
    private final CapabilityAuditStage stage;
    private final CapabilityAuditStepStatus status;
    private final String capabilityName;
    private final Long tenantId;
    private final String summary;
    private final String errorCode;
    private final String errorMessage;
    private final long latencyMs;

}
