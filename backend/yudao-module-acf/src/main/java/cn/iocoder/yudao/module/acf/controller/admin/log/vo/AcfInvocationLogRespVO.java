package cn.iocoder.yudao.module.acf.controller.admin.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - ACF 能力调用日志 Response VO")
@Data
public class AcfInvocationLogRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;
    @Schema(description = "Trace ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String traceId;
    @Schema(description = "用户编号", example = "1")
    private Long userId;
    @Schema(description = "能力名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "erp.product.search")
    private String capabilityName;
    @Schema(description = "能力版本", example = "1.0.0")
    private String capabilityVersion;
    @Schema(description = "调用来源", example = "MCP")
    private String source;
    @Schema(description = "消费者类型", example = "MCP")
    private String consumerType;
    @Schema(description = "消费者编号", example = "codex")
    private String consumerId;
    @Schema(description = "客户端请求编号")
    private String clientRequestId;
    @Schema(description = "请求摘要")
    private String requestSummary;
    @Schema(description = "响应摘要")
    private String responseSummary;
    @Schema(description = "治理摘要")
    private String policySummary;
    @Schema(description = "运行时摘要")
    private String runtimeSummary;
    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(description = "错误码")
    private String errorCode;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "耗时毫秒", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long latencyMs;
    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
