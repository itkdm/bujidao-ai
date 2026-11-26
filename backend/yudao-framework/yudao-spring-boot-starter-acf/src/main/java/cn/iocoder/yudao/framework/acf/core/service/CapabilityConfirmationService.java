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
                                                     String idempotencyKey, String requestDigest);

    CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition, CapabilityContext context,
                                                       String confirmationToken, String idempotencyKey,
                                                       String requestDigest);

    default CapabilityConfirmationToken confirm(String challengeId, CapabilityContext context, String confirmRemark) {
        throw new UnsupportedOperationException("Capability confirmation is not configured");
    }

    /**
     * 用户拒绝确认：把仍在 {@code PENDING} 的 challenge 置为终态，之后再用它
     * 调 {@link #confirm} 会因“非 pending”被拒绝。拒绝不授权任何执行，所以
     * 本方法是幂等的、尽力而为的：challenge 不存在、已终态、已过期或不属于
     * 当前调用方时直接返回，不抛异常。
     */
    default void decline(String challengeId, CapabilityContext context) {
        throw new UnsupportedOperationException("Capability confirmation is not configured");
    }

}
