package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConfirmationStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyAuditStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 一次能力调用的最终审计记录
 *
 * 这里只记录治理和执行元数据，不默认保存原始请求与响应，避免 Starter
 * 在缺少业务脱敏规则时持久化敏感内容。业务模块可以通过 traceId 关联自己的明细日志。
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityAuditRecord {

    private final String traceId;
    private final String capabilityName;
    private final String capabilityVersion;
    private final Long userId;
    private final Long tenantId;
    private final String source;
    private final CapabilityConsumerType consumerType;
    private final String consumerId;
    private final String clientRequestId;
    private final CapabilityAuditStage finalStage;
    private final CapabilityConfirmationStatus confirmationStatus;
    private final CapabilityIdempotencyAuditStatus idempotencyStatus;
    private final String runtimePolicySummary;
    private final String runtimeGuardCode;
    private final int retryCount;
    private final boolean targetInvoked;
    private final CapabilityStatus status;
    private final String errorCode;
    private final String message;
    private final boolean retryable;
    private final long latencyMs;

}
