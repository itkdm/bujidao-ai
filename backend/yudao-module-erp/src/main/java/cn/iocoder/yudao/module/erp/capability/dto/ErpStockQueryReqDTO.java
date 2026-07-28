package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ERP 库存查询能力入参
 *
 * @author bujidao
 */
@Data
public class ErpStockQueryReqDTO {

    @NotNull
    @CapabilityField(description = "商品编号，可先通过 erp.product.search 获取", example = "1")
    private Long productId;

    @CapabilityField(description = "仓库编号；为空时返回全部仓库的库存明细", example = "1")
    private Long warehouseId;

}
