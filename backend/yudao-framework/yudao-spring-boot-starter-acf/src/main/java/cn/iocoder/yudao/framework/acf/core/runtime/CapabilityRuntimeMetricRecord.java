package cn.iocoder.yudao.framework.acf.core.runtime;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 单次能力调用的运行指标事件
 *
 * 事件只携带适合指标聚合的低敏执行元数据，不包含 traceId、用户标识、请求参数或响应内容。
 * 指标实现可据此统计调用量、成功率、失败类型、治理拒绝、重试次数与调用耗时。
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityRuntimeMetricRecord {

    private final String capabilityName;
    private final String capabilityVersion;
    private final CapabilityAuditStage finalStage;
    private final CapabilityStatus status;
    private final String errorCode;
    private final String runtimeGuardCode;
    private final int retryCount;
    private final boolean targetInvoked;
    private final boolean retryable;
    private final long latencyMs;

}
