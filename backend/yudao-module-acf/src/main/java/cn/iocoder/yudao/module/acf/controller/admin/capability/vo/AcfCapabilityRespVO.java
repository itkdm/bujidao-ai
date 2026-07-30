package cn.iocoder.yudao.module.acf.controller.admin.capability.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - ACF 能力定义 Response VO")
@Data
public class AcfCapabilityRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;
    @Schema(description = "能力名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "erp.product.search")
    private String capabilityName;
    @Schema(description = "能力版本", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0.0")
    private String capabilityVersion;
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "查询商品")
    private String title;
    @Schema(description = "描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;
    @Schema(description = "分类", example = "ERP")
    private String category;
    @Schema(description = "风险等级", example = "LOW")
    private String riskLevel;
    @Schema(description = "是否有副作用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean sideEffect;
    @Schema(description = "是否需要确认", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean confirmationRequired;
    @Schema(description = "权限模式", example = "ALL")
    private String permissionMode;
    @Schema(description = "权限标识 JSON")
    private String permissionsJson;
    @Schema(description = "超时时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer timeoutMs;
    @Schema(description = "入参 Java 类型")
    private String argumentType;
    @Schema(description = "返回 Java 类型")
    private String returnType;
    @Schema(description = "入参 Schema")
    private String inputSchemaJson;
    @Schema(description = "出参 Schema")
    private String outputSchemaJson;
    @Schema(description = "定义摘要")
    private String definitionDigest;
    @Schema(description = "运行时状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String runtimeStatus;
    @Schema(description = "最后扫描时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime lastScanTime;
    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
