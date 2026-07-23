package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStepStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConfirmationStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyAuditStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditStepRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单次能力调用的审计状态收集器
 *
 * 该类只收集执行元数据并调用审计 SPI，不参与业务结果判断。审计实现异常会被隔离，
 * 避免已经完成的能力调用因为旁路记录失败而改变返回结果。
 *
 * @author bujidao
 */
final class CapabilityExecutionAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger(CapabilityExecutionAudit.class);

    private final CapabilityAuditService auditService;
    private final String traceId;
    private final String capabilityName;
    private final CapabilityContext context;
    private final long startedAt;

    private CapabilityDefinition definition;
    private CapabilityAuditStage finalStage = CapabilityAuditStage.REQUEST_VALIDATION;
    private CapabilityAuditStage failureStage;
    private CapabilityConfirmationStatus confirmationStatus = CapabilityConfirmationStatus.NOT_REQUIRED;
    private CapabilityIdempotencyAuditStatus idempotencyStatus = CapabilityIdempotencyAuditStatus.NOT_REQUESTED;
    private boolean targetInvoked;
    private int stepNo;

    CapabilityExecutionAudit(CapabilityAuditService auditService, String traceId, String capabilityName,
                             CapabilityContext context, long startedAt) {
        this.auditService = auditService;
        this.traceId = traceId;
        this.capabilityName = capabilityName;
        this.context = context;
        this.startedAt = startedAt;
    }

    void definition(CapabilityDefinition definition) {
        this.definition = definition;
    }

    void confirmationStatus(CapabilityConfirmationStatus confirmationStatus) {
        this.confirmationStatus = confirmationStatus;
    }

    void idempotencyStatus(CapabilityIdempotencyAuditStatus idempotencyStatus) {
        this.idempotencyStatus = idempotencyStatus;
    }

    void targetInvoked() {
        this.targetInvoked = true;
    }

    void success(CapabilityAuditStage stage, String summary, long stepStartedAt) {
        finalStage = stage;
        recordStep(CapabilityAuditStepStatus.SUCCESS, stage, summary, null, null, stepStartedAt);
    }

    void failure(CapabilityAuditStage stage, CapabilityResult result, long stepStartedAt) {
        finalStage = stage;
        failureStage = stage;
        recordStep(CapabilityAuditStepStatus.FAILURE, stage, result == null ? null : result.getMessage(),
                result == null ? null : result.getErrorCode(), result == null ? null : result.getMessage(),
                stepStartedAt);
    }

    void finish(CapabilityResult result) {
        if (result != null && result.isSuccess()) {
            finalStage = CapabilityAuditStage.COMPLETED;
        } else if (failureStage != null) {
            finalStage = failureStage;
        }
        if (auditService == null) {
            return;
        }
        try {
            auditService.record(CapabilityAuditRecord.builder()
                    .traceId(traceId)
                    .capabilityName(capabilityName)
                    .capabilityVersion(definition == null ? null : definition.getVersion())
                    .userId(context.getUserId())
                    .tenantId(context.getTenantId())
                    .source(context.getSource())
                    .consumerType(context.getConsumerType())
                    .consumerId(context.getConsumerId())
                    .clientRequestId(context.getClientRequestId())
                    .finalStage(finalStage)
                    .confirmationStatus(confirmationStatus)
                    .idempotencyStatus(idempotencyStatus)
                    .targetInvoked(targetInvoked)
                    .status(result == null ? null : result.getStatus())
                    .errorCode(result == null ? null : result.getErrorCode())
                    .message(result == null ? null : result.getMessage())
                    .retryable(result != null && result.isRetryable())
                    .latencyMs(elapsed(startedAt))
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.warn("记录 ACF 能力执行审计失败，traceId={}，capability={}",
                    traceId, capabilityName, exception);
        }
    }

    private void recordStep(CapabilityAuditStepStatus status, CapabilityAuditStage stage, String summary,
                            String errorCode, String errorMessage, long stepStartedAt) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.recordStep(CapabilityAuditStepRecord.builder()
                    .traceId(traceId)
                    .stepNo(++stepNo)
                    .stage(stage)
                    .status(status)
                    .capabilityName(capabilityName)
                    .tenantId(context.getTenantId())
                    .summary(summary)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .latencyMs(elapsed(stepStartedAt))
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.warn("记录 ACF 能力执行步骤失败，traceId={}，capability={}，stage={}",
                    traceId, capabilityName, stage, exception);
        }
    }

    private long elapsed(long startTime) {
        return Math.max(0, System.currentTimeMillis() - startTime);
    }

}
