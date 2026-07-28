package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 商品能力视图
 *
 * 只暴露 Agent 决策需要的字段，不直接返回 ERP 内部 DO 或管理后台 VO。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpProductCapabilityDTO {

    @CapabilityField(description = "商品编号")
    private Long id;

    @CapabilityField(description = "商品名称")
    private String name;

    @CapabilityField(description = "商品条码")
    private String barCode;

    @CapabilityField(description = "商品分类编号")
    private Long categoryId;

    @CapabilityField(description = "商品分类名称")
    private String categoryName;

    @CapabilityField(description = "单位名称")
    private String unitName;

    @CapabilityField(description = "商品状态：0 开启，1 关闭")
    private Integer status;

    @CapabilityField(description = "商品规格")
    private String standard;

    @CapabilityField(description = "采购价格，单位：元")
    private BigDecimal purchasePrice;

    @CapabilityField(description = "销售价格，单位：元")
    private BigDecimal salePrice;

}
