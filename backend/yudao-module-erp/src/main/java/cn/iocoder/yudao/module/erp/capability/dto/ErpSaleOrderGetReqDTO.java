package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ERP 销售订单查询能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpSaleOrderGetReqDTO {

    @NotNull(message = "销售订单编号不能为空")
    @CapabilityField(description = "销售订单编号", example = "1001")
    private Long id;

}
