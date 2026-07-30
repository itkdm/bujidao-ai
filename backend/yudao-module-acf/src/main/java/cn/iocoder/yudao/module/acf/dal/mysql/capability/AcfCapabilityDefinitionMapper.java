package cn.iocoder.yudao.module.acf.dal.mysql.capability;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.acf.controller.admin.capability.vo.AcfCapabilityPageReqVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.capability.AcfCapabilityDefinitionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ACF 能力定义 Mapper
 *
 * @author bujidao
 */
@Mapper
public interface AcfCapabilityDefinitionMapper extends BaseMapperX<AcfCapabilityDefinitionDO> {

    Long GLOBAL_TENANT_ID = 0L;

    default PageResult<AcfCapabilityDefinitionDO> selectPage(AcfCapabilityPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AcfCapabilityDefinitionDO>()
                .eq(AcfCapabilityDefinitionDO::getTenantId, GLOBAL_TENANT_ID)
                .likeIfPresent(AcfCapabilityDefinitionDO::getCapabilityName, reqVO.getCapabilityName())
                .eqIfPresent(AcfCapabilityDefinitionDO::getCategory, reqVO.getCategory())
                .eqIfPresent(AcfCapabilityDefinitionDO::getRiskLevel, reqVO.getRiskLevel())
                .eqIfPresent(AcfCapabilityDefinitionDO::getRuntimeStatus, reqVO.getRuntimeStatus())
                .orderByAsc(AcfCapabilityDefinitionDO::getCapabilityName));
    }

    default AcfCapabilityDefinitionDO selectByNameVersion(String capabilityName, String capabilityVersion) {
        return selectOne(new LambdaQueryWrapperX<AcfCapabilityDefinitionDO>()
                .eq(AcfCapabilityDefinitionDO::getTenantId, GLOBAL_TENANT_ID)
                .eq(AcfCapabilityDefinitionDO::getCapabilityName, capabilityName)
                .eq(AcfCapabilityDefinitionDO::getCapabilityVersion, capabilityVersion));
    }

    default List<AcfCapabilityDefinitionDO> selectListByGlobalTenant() {
        return selectList(new LambdaQueryWrapperX<AcfCapabilityDefinitionDO>()
                .eq(AcfCapabilityDefinitionDO::getTenantId, GLOBAL_TENANT_ID));
    }

}
