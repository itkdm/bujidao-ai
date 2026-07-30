package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ERP 金额统计能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpStatisticsSummaryReqDTO {

    @CapabilityField(description = "自定义统计开始时间；为空时只返回今日、昨日、本月、本年汇总")
    private LocalDateTime beginTime;

    @CapabilityField(description = "自定义统计结束时间；为空表示截至当前")
    private LocalDateTime endTime;

}
