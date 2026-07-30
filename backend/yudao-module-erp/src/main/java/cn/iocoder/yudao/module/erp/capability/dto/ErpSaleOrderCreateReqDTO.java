package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 销售订单创建能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpSaleOrderCreateReqDTO {

    @NotNull(message = "客户编号不能为空")
    @CapabilityField(description = "客户编号", example = "1001")
    private Long customerId;

    @CapabilityField(description = "下单时间；为空时使用当前时间")
    private LocalDateTime orderTime;

    @CapabilityField(description = "优惠率，百分比；为空时按 0 处理", example = "0")
    private BigDecimal discountPercent;

    @CapabilityField(description = "定金金额，单位：元；为空时按 0 处理", example = "0")
    private BigDecimal depositPrice;

    @Size(max = 255)
    @CapabilityField(description = "备注", example = "MCP 创建销售订单")
    private String remark;

    @Valid
    @NotEmpty(message = "订单明细不能为空")
    @CapabilityField(description = "订单明细")
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "商品编号不能为空")
        @CapabilityField(description = "商品编号", example = "1001")
        private Long productId;

        @DecimalMin(value = "0", inclusive = false, message = "商品数量必须大于 0")
        @NotNull(message = "商品数量不能为空")
        @CapabilityField(description = "商品数量", example = "2")
        private BigDecimal count;

        @DecimalMin(value = "0", message = "商品单价不能小于 0")
        @CapabilityField(description = "商品单价，单位：元；为空时使用商品销售价", example = "99.00")
        private BigDecimal productPrice;

        @DecimalMin(value = "0", message = "税率不能小于 0")
        @CapabilityField(description = "税率，百分比；为空时按 0 处理", example = "0")
        private BigDecimal taxPercent;

        @Size(max = 255)
        @CapabilityField(description = "备注", example = "MCP 创建销售订单明细")
        private String remark;

    }

}
