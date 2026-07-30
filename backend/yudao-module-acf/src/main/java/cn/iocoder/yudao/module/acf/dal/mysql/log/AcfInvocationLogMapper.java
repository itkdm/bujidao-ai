package cn.iocoder.yudao.module.acf.dal.mysql.log;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.acf.controller.admin.log.vo.AcfInvocationLogPageReqVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.log.AcfInvocationLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * ACF 能力调用日志 Mapper
 *
 * @author bujidao
 */
@Mapper
public interface AcfInvocationLogMapper extends BaseMapperX<AcfInvocationLogDO> {

    default PageResult<AcfInvocationLogDO> selectPage(AcfInvocationLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AcfInvocationLogDO>()
                .likeIfPresent(AcfInvocationLogDO::getCapabilityName, reqVO.getCapabilityName())
                .eqIfPresent(AcfInvocationLogDO::getConsumerType, reqVO.getConsumerType())
                .eqIfPresent(AcfInvocationLogDO::getConsumerId, reqVO.getConsumerId())
                .eqIfPresent(AcfInvocationLogDO::getUserId, reqVO.getUserId())
                .eqIfPresent(AcfInvocationLogDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(AcfInvocationLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(AcfInvocationLogDO::getId));
    }

}
