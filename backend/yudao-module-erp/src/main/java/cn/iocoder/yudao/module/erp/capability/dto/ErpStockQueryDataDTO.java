package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * ERP 库存查询能力返回数据
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpStockQueryDataDTO {

    @CapabilityField(description = "商品编号")
    private Long productId;

    @CapabilityField(description = "查询时指定的仓库编号；未指定时为空")
    private Long warehouseId;

    @CapabilityField(description = "商品全部仓库的库存合计数量")
    private BigDecimal totalCount;

    @CapabilityField(description = "分仓库存明细")
    private List<WarehouseStock> warehouseStocks;

    /**
     * 分仓库存明细
     */
    @Data
    @Builder
    public static class WarehouseStock {

        @CapabilityField(description = "仓库编号")
        private Long warehouseId;

        @CapabilityField(description = "仓库名称")
        private String warehouseName;

        @CapabilityField(description = "该仓库的库存数量")
        private BigDecimal count;

    }

}
