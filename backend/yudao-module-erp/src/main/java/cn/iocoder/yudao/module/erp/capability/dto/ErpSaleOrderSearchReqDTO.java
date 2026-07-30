package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ERP 销售订单查询能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpSaleOrderSearchReqDTO {

    @Size(max = 64)
    @CapabilityField(description = "销售订单号关键词；为空时不限制订单号", example = "XS")
    private String no;

    @CapabilityField(description = "客户编号；为空时不限制客户", example = "1001")
    private Long customerId;

    @CapabilityField(description = "商品编号；为空时不限制商品", example = "1001")
    private Long productId;

    @CapabilityField(description = "审核状态；为空时不限制状态", example = "20")
    private Integer status;

    @Min(1)
    @CapabilityField(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Min(1)
    @Max(50)
    @CapabilityField(description = "每页条数，最大 50", example = "10")
    private Integer pageSize = 10;

}
