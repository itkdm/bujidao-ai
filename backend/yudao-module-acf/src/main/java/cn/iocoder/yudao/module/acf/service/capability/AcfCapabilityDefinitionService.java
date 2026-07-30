package cn.iocoder.yudao.module.acf.service.capability;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilityPageReqVO;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilitySyncRespVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.capability.AcfCapabilityDefinitionDO;

/**
 * ACF 能力定义 Service
 *
 * @author bujidao
 */
public interface AcfCapabilityDefinitionService {

    PageResult<AcfCapabilityDefinitionDO> getCapabilityDefinitionPage(AcfCapabilityPageReqVO pageReqVO);

    AcfCapabilityDefinitionDO getCapabilityDefinition(Long id);

    AcfCapabilityDefinitionDO getRuntimeDefinition(String capabilityName, String capabilityVersion);

    AcfCapabilitySyncRespVO syncCapabilityDefinitions();

}
