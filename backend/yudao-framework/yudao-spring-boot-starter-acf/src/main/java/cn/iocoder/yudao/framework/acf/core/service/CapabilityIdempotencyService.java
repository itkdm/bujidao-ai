package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;

/**
 * 能力幂等执行服务
 *
 * {@link #acquire} 必须原子地创建或读取幂等记录，不能依赖执行器先查后写。
 * {@link #release} 仅用于目标方法执行前的失败，目标方法一旦开始执行就应调用 complete 或 fail 收口状态。
 *
 * @author bujidao
 */
public interface CapabilityIdempotencyService {

    CapabilityIdempotencyCheck acquire(CapabilityDefinition definition, CapabilityContext context,
                                       String idempotencyKey, String requestDigest);

    void complete(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                  String requestDigest, CapabilityResult result);

    void fail(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
              String requestDigest, CapabilityResult result);

    void release(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                 String requestDigest);

}
