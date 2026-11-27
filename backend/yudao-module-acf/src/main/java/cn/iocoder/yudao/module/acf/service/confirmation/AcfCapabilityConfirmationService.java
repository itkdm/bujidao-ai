package cn.iocoder.yudao.module.acf.service.confirmation;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import cn.iocoder.yudao.module.acf.dal.dataobject.confirmation.AcfConfirmationChallengeDO;
import cn.iocoder.yudao.module.acf.dal.mysql.confirmation.AcfConfirmationChallengeMapper;
import cn.iocoder.yudao.module.acf.enums.AcfConfirmationChallengeStatus;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 基于数据库的 ACF 确认挑战服务。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
public class AcfCapabilityConfirmationService implements CapabilityConfirmationService {

    private static final int EXPIRE_MINUTES = 10;
    private static final int CONFIRM_REMARK_MAX_LENGTH = 512;
    private static final String CONSUMER_CLIENT_ID_ATTRIBUTE = "oauthClientId";

    private final AcfConfirmationChallengeMapper confirmationChallengeMapper;

    @Override
    public CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition, CapabilityContext context,
                                                           String idempotencyKey, String requestDigest) {
        LocalDateTime now = LocalDateTime.now();
        Long tenantId = normalizeId(context.getTenantId());
        Long userId = normalizeId(context.getUserId());
        String consumerType = context.getConsumerType() == null ? null : context.getConsumerType().name();
        String consumerClientId = consumerClientId(context);
        AcfConfirmationChallengeDO reusable = confirmationChallengeMapper.selectReusablePending(definition.getName(),
                definition.getVersion(), tenantId, userId, consumerType, context.getConsumerId(), consumerClientId,
                idempotencyKey, requestDigest, now);
        if (reusable != null) {
            return toChallenge(reusable);
        }

        LocalDateTime expiresAt = now.plusMinutes(EXPIRE_MINUTES);
        String challengeId = "acf-confirm-" + UUID.randomUUID();
        AcfConfirmationChallengeDO challenge = AcfConfirmationChallengeDO.builder()
                .challengeId(challengeId)
                .capabilityName(definition.getName())
                .capabilityVersion(definition.getVersion())
                .riskLevel(definition.getRiskLevel() == null ? null : definition.getRiskLevel().name())
                .userId(userId)
                .source(context.getSource())
                .consumerType(consumerType)
                .consumerId(context.getConsumerId())
                .consumerClientId(consumerClientId)
                .clientRequestId(context.getClientRequestId())
                .idempotencyKey(idempotencyKey)
                .requestDigest(requestDigest)
                .status(AcfConfirmationChallengeStatus.PENDING.name())
                .expiresAt(expiresAt)
                .build();
        challenge.setTenantId(tenantId);
        confirmationChallengeMapper.insert(challenge);
        return toChallenge(challenge);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityConfirmationToken confirm(String challengeId, CapabilityContext context, String confirmRemark) {
        if (!StringUtils.hasText(challengeId)) {
            throw new IllegalArgumentException("Confirmation challenge id is required");
        }
        AcfConfirmationChallengeDO challenge = confirmationChallengeMapper.selectByChallengeIdForUpdate(challengeId);
        if (challenge == null) {
            throw new IllegalArgumentException("Confirmation challenge not found");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!AcfConfirmationChallengeStatus.PENDING.name().equals(challenge.getStatus())) {
            throw new IllegalArgumentException("Confirmation challenge is not pending");
        }
        if (isExpired(challenge, now)) {
            markExpired(challenge);
            throw new IllegalArgumentException("Confirmation challenge is expired");
        }
        if (!belongsToContext(challenge, context)) {
            throw new IllegalArgumentException("Confirmation challenge does not belong to current caller");
        }

        String confirmationToken = "acf-token-" + UUID.randomUUID();
        challenge.setTokenHash(hash(confirmationToken));
        challenge.setStatus(AcfConfirmationChallengeStatus.CONFIRMED.name());
        challenge.setConfirmedUserId(normalizeId(context.getUserId()));
        challenge.setConfirmedTime(now);
        challenge.setConfirmRemark(truncate(confirmRemark, CONFIRM_REMARK_MAX_LENGTH));
        confirmationChallengeMapper.updateById(challenge);
        return CapabilityConfirmationToken.builder()
                .challengeId(challengeId)
                .confirmationToken(confirmationToken)
                .expiresAt(challenge.getExpiresAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decline(String challengeId, CapabilityContext context) {
        if (!StringUtils.hasText(challengeId)) {
            return;
        }
        AcfConfirmationChallengeDO challenge = confirmationChallengeMapper.selectByChallengeIdForUpdate(challengeId);
        if (challenge == null) {
            return;
        }
        // 已终态（已确认、已消费、已过期、已拒绝）时不再流转；拒绝不授权任何
        // 执行，所以这里不抛异常——拒绝分支的调用方仍按 DENIED 回答。
        if (!AcfConfirmationChallengeStatus.PENDING.name().equals(challenge.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (isExpired(challenge, now)) {
            markExpired(challenge);
            return;
        }
        if (!belongsToContext(challenge, context)) {
            return;
        }
        challenge.setStatus(AcfConfirmationChallengeStatus.DECLINED.name());
        confirmationChallengeMapper.updateById(challenge);
    }

    @Override
    public CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition, CapabilityContext context,
                                                             String confirmationToken, String idempotencyKey,
                                                             String requestDigest) {
        if (!StringUtils.hasText(confirmationToken)) {
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token is required");
        }
        String tokenHash = hash(confirmationToken);
        AcfConfirmationChallengeDO challenge = confirmationChallengeMapper.selectByTokenHash(tokenHash);
        if (challenge == null) {
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token is invalid");
        }
        if (!matchesInvocation(challenge, definition, context, idempotencyKey, requestDigest)) {
            // 摘要失配时把已确认的 challenge 打回 PENDING：否则状态卡在 CONFIRMED，
            // 客户端重调 confirm() 会因“非 pending”被拒，而 token 只存在 handler
            // 局部变量、失败即丢，整个 challenge 就烧掉了。打回后客户端可重新确认、
            // 换新 token 按原始摘要重试（challenge 仍绑定原始摘要，改过的调用仍会被拒）。
            // 条件更新（与消费更新同款写法）：只在仍为 CONFIRMED 且未过期时打回，防并发竞态。
            if (AcfConfirmationChallengeStatus.CONFIRMED.name().equals(challenge.getStatus())) {
                AcfConfirmationChallengeDO reset = new AcfConfirmationChallengeDO();
                reset.setStatus(AcfConfirmationChallengeStatus.PENDING.name());
                confirmationChallengeMapper.update(reset, new LambdaUpdateWrapper<AcfConfirmationChallengeDO>()
                        .eq(AcfConfirmationChallengeDO::getId, challenge.getId())
                        .eq(AcfConfirmationChallengeDO::getTokenHash, tokenHash)
                        .eq(AcfConfirmationChallengeDO::getStatus, AcfConfirmationChallengeStatus.CONFIRMED.name())
                        .gt(AcfConfirmationChallengeDO::getExpiresAt, LocalDateTime.now()));
            }
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token does not match current request");
        }
        if (!AcfConfirmationChallengeStatus.CONFIRMED.name().equals(challenge.getStatus())) {
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token is not usable");
        }
        LocalDateTime now = LocalDateTime.now();
        if (isExpired(challenge, now)) {
            markExpired(challenge);
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token is expired");
        }

        AcfConfirmationChallengeDO update = new AcfConfirmationChallengeDO();
        update.setStatus(AcfConfirmationChallengeStatus.USED.name());
        update.setUsedTime(now);
        update.setUsedTraceId(context.getTraceId());
        int updated = confirmationChallengeMapper.update(update, new LambdaUpdateWrapper<AcfConfirmationChallengeDO>()
                .eq(AcfConfirmationChallengeDO::getId, challenge.getId())
                .eq(AcfConfirmationChallengeDO::getTokenHash, tokenHash)
                .eq(AcfConfirmationChallengeDO::getStatus, AcfConfirmationChallengeStatus.CONFIRMED.name())
                .gt(AcfConfirmationChallengeDO::getExpiresAt, now));
        if (updated != 1) {
            return CapabilityConfirmationCheck.invalid(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID,
                    "Confirmation token has already been consumed");
        }
        return CapabilityConfirmationCheck.valid(challenge.getChallengeId());
    }

    private boolean matchesInvocation(AcfConfirmationChallengeDO challenge, CapabilityDefinition definition,
                                      CapabilityContext context, String idempotencyKey, String requestDigest) {
        return Objects.equals(challenge.getCapabilityName(), definition.getName())
                && Objects.equals(challenge.getCapabilityVersion(), definition.getVersion())
                && Objects.equals(challenge.getTenantId(), normalizeId(context.getTenantId()))
                && Objects.equals(challenge.getUserId(), normalizeId(context.getUserId()))
                && Objects.equals(challenge.getConsumerType(),
                        context.getConsumerType() == null ? null : context.getConsumerType().name())
                && Objects.equals(challenge.getConsumerId(), context.getConsumerId())
                && Objects.equals(challenge.getConsumerClientId(), consumerClientId(context))
                && Objects.equals(challenge.getIdempotencyKey(), idempotencyKey)
                && Objects.equals(challenge.getRequestDigest(), requestDigest);
    }

    private boolean belongsToContext(AcfConfirmationChallengeDO challenge, CapabilityContext context) {
        return Objects.equals(challenge.getTenantId(), normalizeId(context.getTenantId()))
                && Objects.equals(challenge.getUserId(), normalizeId(context.getUserId()))
                && Objects.equals(challenge.getConsumerType(),
                        context.getConsumerType() == null ? null : context.getConsumerType().name())
                && Objects.equals(challenge.getConsumerId(), context.getConsumerId())
                && Objects.equals(challenge.getConsumerClientId(), consumerClientId(context));
    }

    private void markExpired(AcfConfirmationChallengeDO challenge) {
        challenge.setStatus(AcfConfirmationChallengeStatus.EXPIRED.name());
        confirmationChallengeMapper.updateById(challenge);
    }

    private boolean isExpired(AcfConfirmationChallengeDO challenge, LocalDateTime now) {
        return challenge.getExpiresAt() != null && !challenge.getExpiresAt().isAfter(now);
    }

    private CapabilityConfirmationChallenge toChallenge(AcfConfirmationChallengeDO challenge) {
        return CapabilityConfirmationChallenge.builder()
                .challengeId(challenge.getChallengeId())
                .capabilityName(challenge.getCapabilityName())
                .capabilityVersion(challenge.getCapabilityVersion())
                .riskLevel(challenge.getRiskLevel() == null ? null
                        : cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel.valueOf(challenge.getRiskLevel()))
                .expiresAt(challenge.getExpiresAt())
                .requestDigest(challenge.getRequestDigest())
                .build();
    }

    private Long normalizeId(Long id) {
        return id == null ? 0L : id;
    }

    private String consumerClientId(CapabilityContext context) {
        Object value = context.getAttributes().get(CONSUMER_CLIENT_ID_ATTRIBUTE);
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                    messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

}
