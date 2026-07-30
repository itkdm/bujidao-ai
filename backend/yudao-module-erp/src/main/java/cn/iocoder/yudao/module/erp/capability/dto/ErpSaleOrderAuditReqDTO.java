package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ERP 销售订单审核能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpSaleOrderAuditReqDTO {

    @NotNull(message = "销售订单编号不能为空")
    @CapabilityField(description = "销售订单编号", example = "1001")
    private Long id;

    @NotNull(message = "是否审核通过不能为空")
    @CapabilityField(description = "true 表示审核通过，false 表示反审核为未审核", example = "true")
    private Boolean approved;

}
