package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ERP 库存流水查询能力入参。
 *
 * @author bujidao
 */
@Data
public class ErpStockRecordSearchReqDTO {

    @CapabilityField(description = "商品编号；为空时不限制商品", example = "1001")
    private Long productId;

    @CapabilityField(description = "仓库编号；为空时不限制仓库", example = "1")
    private Long warehouseId;

    @CapabilityField(description = "业务类型；为空时不限制类型，例如 50 表示销售出库，70 表示采购入库", example = "50")
    private Integer bizType;

    @Size(max = 64)
    @CapabilityField(description = "业务单号；为空时不限制单号", example = "XS")
    private String bizNo;

    @CapabilityField(description = "创建时间开始；为空时不限制开始时间")
    private LocalDateTime beginTime;

    @CapabilityField(description = "创建时间结束；为空时不限制结束时间")
    private LocalDateTime endTime;

    @Min(1)
    @CapabilityField(description = "页码，从 1 开始", example = "1")
    private Integer pageNo = 1;

    @Min(1)
    @Max(50)
    @CapabilityField(description = "每页条数，最大 50", example = "10")
    private Integer pageSize = 10;

}
