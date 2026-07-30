package cn.iocoder.yudao.module.acf.service.log;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditStepRecord;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityAuditService;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.acf.controller.admin.log.vo.AcfInvocationLogPageReqVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.log.AcfInvocationLogDO;
import cn.iocoder.yudao.module.acf.dal.mysql.log.AcfInvocationLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ACF 能力调用日志 Service 实现类
 *
 * @author bujidao
 */
@Service
@Slf4j
public class AcfInvocationLogServiceImpl implements AcfInvocationLogService, CapabilityAuditService {

    private static final int SUMMARY_LENGTH = 1024;

    @Resource
    private AcfInvocationLogMapper invocationLogMapper;

    @Override
    public PageResult<AcfInvocationLogDO> getInvocationLogPage(AcfInvocationLogPageReqVO pageReqVO) {
        return invocationLogMapper.selectPage(pageReqVO);
    }

    @Override
    public AcfInvocationLogDO getInvocationLog(Long id) {
        return invocationLogMapper.selectById(id);
    }

    @Override
    public void record(CapabilityAuditRecord record) {
        try {
            AcfInvocationLogDO logDO = AcfInvocationLogDO.builder()
                    .traceId(record.getTraceId())
                    .userId(record.getUserId())
                    .capabilityName(record.getCapabilityName())
                    .capabilityVersion(record.getCapabilityVersion())
                    .source(record.getSource())
                    .consumerType(record.getConsumerType() == null ? null : record.getConsumerType().name())
                    .consumerId(record.getConsumerId())
                    .clientRequestId(record.getClientRequestId())
                    .requestSummary(truncate(buildRequestSummary(record)))
                    .responseSummary(truncate(record.getMessage()))
                    .policySummary(truncate(buildPolicySummary(record)))
                    .runtimeSummary(truncate(buildRuntimeSummary(record)))
                    .status(record.getStatus() == null ? null : record.getStatus().name())
                    .errorCode(record.getErrorCode())
                    .errorMessage(truncate(record.getMessage()))
                    .latencyMs(record.getLatencyMs())
                    .build();
            logDO.setTenantId(record.getTenantId() == null ? 0L : record.getTenantId());
            invocationLogMapper.insert(logDO);
        } catch (Exception exception) {
            log.warn("Failed to persist ACF invocation audit log, traceId={}", record.getTraceId(), exception);
        }
    }

    @Override
    public void recordStep(CapabilityAuditStepRecord record) {
        if (log.isDebugEnabled()) {
            log.debug("ACF audit step ignored by lightweight module, traceId={}, stepNo={}, stage={}, status={}",
                    record.getTraceId(), record.getStepNo(), record.getStage(), record.getStatus());
        }
    }

    private String buildRequestSummary(CapabilityAuditRecord record) {
        if (!StrUtil.isBlank(record.getClientRequestId())) {
            return "clientRequestId=" + record.getClientRequestId();
        }
        return null;
    }

    private String buildPolicySummary(CapabilityAuditRecord record) {
        return "stage=" + nullSafe(record.getFinalStage())
                + ", confirmation=" + nullSafe(record.getConfirmationStatus())
                + ", idempotency=" + nullSafe(record.getIdempotencyStatus());
    }

    private String buildRuntimeSummary(CapabilityAuditRecord record) {
        return "policy=" + nullSafe(record.getRuntimePolicySummary())
                + ", guardCode=" + nullSafe(record.getRuntimeGuardCode())
                + ", retryCount=" + record.getRetryCount()
                + ", targetInvoked=" + record.isTargetInvoked()
                + ", retryable=" + record.isRetryable();
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= SUMMARY_LENGTH) {
            return text;
        }
        return text.substring(0, SUMMARY_LENGTH);
    }

}
