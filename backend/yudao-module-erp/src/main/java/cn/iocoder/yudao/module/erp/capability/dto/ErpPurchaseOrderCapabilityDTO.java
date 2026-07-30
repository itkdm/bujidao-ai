package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * ERP 采购订单能力视图。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpPurchaseOrderCapabilityDTO {

    @CapabilityField(description = "采购订单编号")
    private Long id;

    @CapabilityField(description = "采购订单号")
    private String no;

    @CapabilityField(description = "审核状态")
    private Integer status;

    @CapabilityField(description = "审核状态名称")
    private String statusName;

    @CapabilityField(description = "供应商编号")
    private Long supplierId;

    @CapabilityField(description = "供应商名称")
    private String supplierName;

    @CapabilityField(description = "下单时间")
    private String orderTime;

    @CapabilityField(description = "合计数量")
    private BigDecimal totalCount;

    @CapabilityField(description = "最终合计价格，单位：元")
    private BigDecimal totalPrice;

    @CapabilityField(description = "已入库数量")
    private BigDecimal inCount;

    @CapabilityField(description = "已退货数量")
    private BigDecimal returnCount;

    @CapabilityField(description = "备注")
    private String remark;

    @CapabilityField(description = "采购订单明细")
    private List<Item> items;

    @Data
    @Builder
    public static class Item {

        @CapabilityField(description = "订单项编号")
        private Long id;

        @CapabilityField(description = "商品编号")
        private Long productId;

        @CapabilityField(description = "商品名称")
        private String productName;

        @CapabilityField(description = "单位名称")
        private String unitName;

        @CapabilityField(description = "采购单价，单位：元")
        private BigDecimal productPrice;

        @CapabilityField(description = "采购数量")
        private BigDecimal count;

        @CapabilityField(description = "明细总价，单位：元")
        private BigDecimal totalPrice;

        @CapabilityField(description = "税率，百分比")
        private BigDecimal taxPercent;

        @CapabilityField(description = "已入库数量")
        private BigDecimal inCount;

        @CapabilityField(description = "已退货数量")
        private BigDecimal returnCount;

        @CapabilityField(description = "备注")
        private String remark;

    }

}
