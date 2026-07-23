package cn.iocoder.yudao.framework.acf.core.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 确认令牌校验结果
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityConfirmationCheck {

    private final boolean valid;
    private final String challengeId;
    private final String errorCode;
    private final String reason;

    public static CapabilityConfirmationCheck valid(String challengeId) {
        return CapabilityConfirmationCheck.builder()
                .valid(true)
                .challengeId(challengeId)
                .build();
    }

    public static CapabilityConfirmationCheck invalid(String errorCode, String reason) {
        return CapabilityConfirmationCheck.builder()
                .valid(false)
                .errorCode(errorCode)
                .reason(reason)
                .build();
    }

}
