package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * ERP 销售订单查询能力返回数据。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpSaleOrderSearchDataDTO {

    @CapabilityField(description = "符合条件的总条数")
    private Long total;

    @CapabilityField(description = "本次返回的条数")
    private Integer returnedCount;

    @CapabilityField(description = "销售订单列表")
    private List<ErpSaleOrderCapabilityDTO> list;

}
