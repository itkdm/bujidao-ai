package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditStepRecord;

/**
 * 能力执行审计服务
 *
 * Starter 只定义审计契约，具体持久化由业务模块实现。
 *
 * @author bujidao
 */
public interface CapabilityAuditService {

    void record(CapabilityAuditRecord record);

    default void recordStep(CapabilityAuditStepRecord record) {
    }

}
