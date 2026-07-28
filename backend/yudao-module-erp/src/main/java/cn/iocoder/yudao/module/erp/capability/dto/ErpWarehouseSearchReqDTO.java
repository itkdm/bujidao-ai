package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ERP 仓库查询能力入参
 *
 * @author bujidao
 */
@Data
public class ErpWarehouseSearchReqDTO {

    @Size(max = 64)
    @CapabilityField(description = "仓库名称关键词；为空时返回全部仓库", example = "上海仓")
    private String keyword;

    @CapabilityField(description = "开启状态：0 开启，1 关闭；为空时不限制状态", example = "0")
    private Integer status;

    @Min(1)
    @CapabilityField(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Min(1)
    @Max(50)
    @CapabilityField(description = "每页条数，最大 50", example = "10")
    private Integer pageSize = 10;

}
