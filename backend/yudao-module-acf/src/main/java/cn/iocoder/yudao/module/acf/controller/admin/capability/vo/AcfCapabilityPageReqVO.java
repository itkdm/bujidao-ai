package cn.iocoder.yudao.module.acf.controller.admin.capability.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - ACF 能力定义分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AcfCapabilityPageReqVO extends PageParam {

    @Schema(description = "能力名称", example = "erp.product.search")
    private String capabilityName;

    @Schema(description = "分类", example = "ERP")
    private String category;

    @Schema(description = "风险等级", example = "LOW")
    private String riskLevel;

    @Schema(description = "运行时状态", example = "ACTIVE")
    private String runtimeStatus;

}
