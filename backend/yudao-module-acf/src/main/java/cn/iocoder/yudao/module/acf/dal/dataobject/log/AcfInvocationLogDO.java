package cn.iocoder.yudao.module.acf.dal.dataobject.log;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * ACF 能力调用日志 DO
 *
 * @author bujidao
 */
@TableName("acf_invocation_log")
@KeySequence("acf_invocation_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcfInvocationLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String traceId;
    private Long userId;
    private String capabilityName;
    private String capabilityVersion;
    private String source;
    private String consumerType;
    private String consumerId;
    private String clientRequestId;
    private String requestSummary;
    private String responseSummary;
    private String policySummary;
    private String runtimeSummary;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long latencyMs;

}
