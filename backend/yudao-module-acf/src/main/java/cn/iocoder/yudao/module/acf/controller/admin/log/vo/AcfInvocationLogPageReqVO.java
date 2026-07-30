package cn.iocoder.yudao.module.acf.controller.admin.log.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - ACF 能力调用日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AcfInvocationLogPageReqVO extends PageParam {

    @Schema(description = "能力名称", example = "erp.product.search")
    private String capabilityName;

    @Schema(description = "消费者类型", example = "MCP")
    private String consumerType;

    @Schema(description = "消费者编号", example = "codex")
    private String consumerId;

    @Schema(description = "用户编号", example = "1")
    private Long userId;

    @Schema(description = "状态", example = "SUCCESS")
    private String status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
