package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ERP 采购订单查询能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpPurchaseOrderGetReqDTO {

    @NotNull(message = "采购订单编号不能为空")
    @CapabilityField(description = "采购订单编号", example = "1001")
    private Long id;

}
