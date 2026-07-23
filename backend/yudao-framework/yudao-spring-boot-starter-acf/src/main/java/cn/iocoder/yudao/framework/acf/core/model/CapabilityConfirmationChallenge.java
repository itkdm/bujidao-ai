package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 能力执行确认挑战
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityConfirmationChallenge {

    private final String challengeId;
    private final String capabilityName;
    private final String capabilityVersion;
    private final CapabilityRiskLevel riskLevel;
    private final LocalDateTime expiresAt;
    private final String requestDigest;

}
