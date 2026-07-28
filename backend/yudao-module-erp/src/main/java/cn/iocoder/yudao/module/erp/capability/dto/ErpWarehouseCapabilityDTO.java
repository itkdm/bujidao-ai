package cn.iocoder.yudao.module.erp.capability.dto;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import lombok.Builder;
import lombok.Data;

/**
 * ERP 仓库能力视图
 *
 * @author bujidao
 */
@Data
@Builder
public class ErpWarehouseCapabilityDTO {

    @CapabilityField(description = "仓库编号")
    private Long id;

    @CapabilityField(description = "仓库名称")
    private String name;

    @CapabilityField(description = "仓库地址")
    private String address;

    @CapabilityField(description = "负责人")
    private String principal;

    @CapabilityField(description = "开启状态：0 开启，1 关闭")
    private Integer status;

    @CapabilityField(description = "是否默认仓库")
    private Boolean defaultStatus;

}
