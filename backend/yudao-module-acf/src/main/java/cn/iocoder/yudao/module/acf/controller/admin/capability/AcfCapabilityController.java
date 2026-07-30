package cn.iocoder.yudao.module.acf.controller.admin.capability;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilityPageReqVO;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilityRespVO;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilitySyncRespVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.capability.AcfCapabilityDefinitionDO;
import cn.iocoder.yudao.module.acf.service.capability.AcfCapabilityDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ACF 能力目录")
@RestController
@RequestMapping("/acf/capability")
@Validated
public class AcfCapabilityController {

    @Resource
    private AcfCapabilityDefinitionService capabilityDefinitionService;

    @GetMapping("/page")
    @Operation(summary = "获得 ACF 能力分页")
    @PreAuthorize("@ss.hasPermission('acf:capability:query')")
    public CommonResult<PageResult<AcfCapabilityRespVO>> getCapabilityPage(@Valid AcfCapabilityPageReqVO pageReqVO) {
        PageResult<AcfCapabilityDefinitionDO> pageResult =
                capabilityDefinitionService.getCapabilityDefinitionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AcfCapabilityRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得 ACF 能力")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('acf:capability:query')")
    public CommonResult<AcfCapabilityRespVO> getCapability(@RequestParam("id") Long id) {
        AcfCapabilityDefinitionDO definition = capabilityDefinitionService.getCapabilityDefinition(id);
        return success(BeanUtils.toBean(definition, AcfCapabilityRespVO.class));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步 ACF 能力")
    @PreAuthorize("@ss.hasPermission('acf:capability:sync')")
    public CommonResult<AcfCapabilitySyncRespVO> syncCapabilityDefinitions() {
        return success(capabilityDefinitionService.syncCapabilityDefinitions());
    }

}
