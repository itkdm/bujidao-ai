package cn.iocoder.yudao.module.acf.dal.dataobject.confirmation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ACF 确认挑战 DO
 *
 * @author bujidao
 */
@TableName("acf_confirmation_challenge")
@KeySequence("acf_confirmation_challenge_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcfConfirmationChallengeDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String challengeId;
    private String capabilityName;
    private String capabilityVersion;
    private String riskLevel;
    private Long userId;
    private String source;
    private String consumerType;
    private String consumerId;
    private String consumerClientId;
    private String clientRequestId;
    private String idempotencyKey;
    private String requestDigest;
    private String tokenHash;
    private String status;
    private LocalDateTime expiresAt;
    private Long confirmedUserId;
    private LocalDateTime confirmedTime;
    private String confirmRemark;
    private String usedTraceId;
    private LocalDateTime usedTime;

}
