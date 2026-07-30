package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ERP 库存流水能力视图。
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpStockRecordCapabilityDTO {

    @CapabilityField(description = "库存流水编号")
    private Long id;

    @CapabilityField(description = "商品编号")
    private Long productId;

    @CapabilityField(description = "商品名称")
    private String productName;

    @CapabilityField(description = "仓库编号")
    private Long warehouseId;

    @CapabilityField(description = "仓库名称")
    private String warehouseName;

    @CapabilityField(description = "本次出入库数量；正数表示入库，负数表示出库")
    private BigDecimal count;

    @CapabilityField(description = "本次出入库后的库存数量")
    private BigDecimal totalCount;

    @CapabilityField(description = "业务类型")
    private Integer bizType;

    @CapabilityField(description = "业务类型名称")
    private String bizTypeName;

    @CapabilityField(description = "业务单号")
    private String bizNo;

    @CapabilityField(description = "创建时间")
    private String createTime;

}
