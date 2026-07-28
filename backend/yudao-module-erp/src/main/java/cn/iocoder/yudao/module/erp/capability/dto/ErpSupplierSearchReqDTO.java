package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ERP 供应商查询能力入参
 *
 * keyword 与 mobile 是两个独立条件，同时传入时按「都满足」过滤。
 *
 * @author bujidao
 */
@Data
public class ErpSupplierSearchReqDTO {

    @Size(max = 64)
    @CapabilityField(description = "供应商名称关键词，模糊匹配；为空时返回最近创建的供应商", example = "芋道源码")
    private String keyword;

    @Size(max = 32)
    @CapabilityField(description = "手机号码，模糊匹配；为空时不限制手机号", example = "15601691300")
    private String mobile;

    @Min(1)
    @CapabilityField(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Min(1)
    @Max(50)
    @CapabilityField(description = "每页条数，最大 50", example = "10")
    private Integer pageSize = 10;

}
