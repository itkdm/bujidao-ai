package cn.iocoder.yudao.module.acf.dal.mysql.confirmation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.acf.dal.dataobject.confirmation.AcfConfirmationChallengeDO;
import cn.iocoder.yudao.module.acf.enums.AcfConfirmationChallengeStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * ACF 确认挑战 Mapper
 *
 * @author bujidao
 */
@Mapper
public interface AcfConfirmationChallengeMapper extends BaseMapperX<AcfConfirmationChallengeDO> {

    default AcfConfirmationChallengeDO selectByChallengeIdForUpdate(String challengeId) {
        return selectOneForUpdate(AcfConfirmationChallengeDO::getChallengeId, challengeId);
    }

    default AcfConfirmationChallengeDO selectByTokenHash(String tokenHash) {
        return selectOne(AcfConfirmationChallengeDO::getTokenHash, tokenHash);
    }

    default AcfConfirmationChallengeDO selectReusablePending(String capabilityName, String capabilityVersion,
                                                             Long tenantId, Long userId, String consumerType,
                                                             String consumerId, String consumerClientId,
                                                             String idempotencyKey, String requestDigest,
                                                             LocalDateTime now) {
        return selectOne(new LambdaQueryWrapper<AcfConfirmationChallengeDO>()
                .eq(AcfConfirmationChallengeDO::getCapabilityName, capabilityName)
                .eq(AcfConfirmationChallengeDO::getCapabilityVersion, capabilityVersion)
                .eq(AcfConfirmationChallengeDO::getTenantId, tenantId)
                .eq(AcfConfirmationChallengeDO::getUserId, userId)
                .eq(AcfConfirmationChallengeDO::getConsumerType, consumerType)
                .eq(AcfConfirmationChallengeDO::getConsumerId, consumerId)
                .eq(AcfConfirmationChallengeDO::getConsumerClientId, consumerClientId)
                .eq(AcfConfirmationChallengeDO::getIdempotencyKey, idempotencyKey)
                .eq(AcfConfirmationChallengeDO::getRequestDigest, requestDigest)
                .eq(AcfConfirmationChallengeDO::getStatus, AcfConfirmationChallengeStatus.PENDING.name())
                .gt(AcfConfirmationChallengeDO::getExpiresAt, now)
                .last("LIMIT 1"));
    }

}
