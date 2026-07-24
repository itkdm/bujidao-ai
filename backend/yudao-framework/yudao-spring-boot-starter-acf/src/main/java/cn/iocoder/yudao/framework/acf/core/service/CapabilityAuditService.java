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

    /**
     * Implementations must remain low latency and avoid unbounded blocking on the invocation thread.
     * Buffering, batching, and asynchronous persistence belong to the consuming module. Fields must
     * be redacted and length-limited, and only public-safe error information may be recorded.
     */

    void record(CapabilityAuditRecord record);

    default void recordStep(CapabilityAuditStepRecord record) {
    }

}
