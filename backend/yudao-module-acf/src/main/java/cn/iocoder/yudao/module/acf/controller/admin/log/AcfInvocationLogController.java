package cn.iocoder.yudao.module.acf.controller.admin.log;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.acf.controller.admin.log.vo.AcfInvocationLogPageReqVO;
import cn.iocoder.yudao.module.acf.controller.admin.log.vo.AcfInvocationLogRespVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.log.AcfInvocationLogDO;
import cn.iocoder.yudao.module.acf.service.log.AcfInvocationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ACF 能力调用日志")
@RestController
@RequestMapping("/acf/invocation-log")
@Validated
public class AcfInvocationLogController {

    @Resource
    private AcfInvocationLogService invocationLogService;

    @GetMapping("/page")
    @Operation(summary = "获得 ACF 能力调用日志分页")
    @PreAuthorize("@ss.hasPermission('acf:invocation-log:query')")
    public CommonResult<PageResult<AcfInvocationLogRespVO>> getInvocationLogPage(
            @Valid AcfInvocationLogPageReqVO pageReqVO) {
        PageResult<AcfInvocationLogDO> pageResult = invocationLogService.getInvocationLogPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AcfInvocationLogRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得 ACF 能力调用日志")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('acf:invocation-log:query')")
    public CommonResult<AcfInvocationLogRespVO> getInvocationLog(@RequestParam("id") Long id) {
        AcfInvocationLogDO log = invocationLogService.getInvocationLog(id);
        return success(BeanUtils.toBean(log, AcfInvocationLogRespVO.class));
    }

}
