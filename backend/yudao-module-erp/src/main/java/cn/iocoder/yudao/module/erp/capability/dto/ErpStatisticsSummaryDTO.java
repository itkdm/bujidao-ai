package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 金额统计能力返回数据。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpStatisticsSummaryDTO {

    @CapabilityField(description = "今日金额")
    private BigDecimal todayPrice;

    @CapabilityField(description = "昨日金额")
    private BigDecimal yesterdayPrice;

    @CapabilityField(description = "本月金额")
    private BigDecimal monthPrice;

    @CapabilityField(description = "本年金额")
    private BigDecimal yearPrice;

    @CapabilityField(description = "自定义时间段开始时间")
    private String beginTime;

    @CapabilityField(description = "自定义时间段结束时间；为空表示截至当前")
    private String endTime;

    @CapabilityField(description = "自定义时间段金额；未传 beginTime 时为空")
    private BigDecimal customRangePrice;

}
