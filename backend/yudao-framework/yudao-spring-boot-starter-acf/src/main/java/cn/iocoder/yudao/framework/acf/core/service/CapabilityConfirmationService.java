package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;

/**
 * 能力执行确认服务
 *
 * 挑战的持久化、归属校验、过期控制和令牌签发由业务模块实现。
 * {@link #verifyAndConsumeToken} 必须以原子方式完成令牌校验与消费，避免同一令牌被并发复用。
 *
 * @author bujidao
 */
public interface CapabilityConfirmationService {

    CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition, CapabilityContext context,
                                                     String requestDigest);

    CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition, CapabilityContext context,
                                                       String confirmationToken, String requestDigest);

    default CapabilityConfirmationToken confirm(String challengeId, CapabilityContext context, String confirmRemark) {
        throw new UnsupportedOperationException("Capability confirmation is not configured");
    }

}
