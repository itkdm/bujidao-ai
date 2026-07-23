package cn.iocoder.yudao.framework.acf.core.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户确认后签发的能力确认令牌
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityConfirmationToken {

    private final String challengeId;
    private final String confirmationToken;
    private final LocalDateTime expiresAt;

}
