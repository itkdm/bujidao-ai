package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ERP 商品查询能力入参
 *
 * @author bujidao
 */
@Data
public class ErpProductSearchReqDTO {

    @Size(max = 64)
    @CapabilityField(description = "商品名称或条码关键词；为空时返回最近创建的商品", example = "苹果")
    private String keyword;

    @CapabilityField(description = "商品分类编号；为空时不限制分类", example = "1")
    private Long categoryId;

    @Min(1)
    @CapabilityField(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Min(1)
    @Max(50)
    @CapabilityField(description = "每页条数，最大 50", example = "10")
    private Integer pageSize = 10;

}
