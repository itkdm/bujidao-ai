package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * ERP 库存流水查询能力返回数据。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpStockRecordSearchDataDTO {

    @CapabilityField(description = "符合条件的总条数")
    private Long total;

    @CapabilityField(description = "本次返回的条数")
    private Integer returnedCount;

    @CapabilityField(description = "库存流水列表")
    private List<ErpStockRecordCapabilityDTO> list;

}
