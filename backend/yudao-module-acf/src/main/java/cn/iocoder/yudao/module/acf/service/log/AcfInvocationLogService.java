package cn.iocoder.yudao.module.acf.service.log;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.acf.controller.admin.log.vo.AcfInvocationLogPageReqVO;
import cn.iocoder.yudao.module.acf.dal.dataobject.log.AcfInvocationLogDO;

/**
 * ACF 能力调用日志 Service
 *
 * @author bujidao
 */
public interface AcfInvocationLogService {

    PageResult<AcfInvocationLogDO> getInvocationLogPage(AcfInvocationLogPageReqVO pageReqVO);

    AcfInvocationLogDO getInvocationLog(Long id);

}
