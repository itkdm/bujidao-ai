package cn.iocoder.yudao.module.acf.controller.admin.capability.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - ACF 能力同步 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcfCapabilitySyncRespVO {

    @Schema(description = "扫描数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer scannedCount;
    @Schema(description = "新增数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer createdCount;
    @Schema(description = "更新数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer updatedCount;
    @Schema(description = "缺失数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer missingCount;

}
