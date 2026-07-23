package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 能力执行结果
 *
 * @author bujidao
 */
@Getter
@Builder
public class CapabilityResult {

    private final String name;
    private final CapabilityStatus status;
    private final Object data;
    private final String errorCode;
    private final String message;

    public static CapabilityResult success(String name, Object data) {
        return CapabilityResult.builder()
                .name(name)
                .status(CapabilityStatus.SUCCESS)
                .data(data)
                .build();
    }

    public static CapabilityResult confirmationRequired(String name, CapabilityConfirmationChallenge challenge) {
        return CapabilityResult.builder()
                .name(name)
                .status(CapabilityStatus.CONFIRM_REQUIRED)
                .data(challenge)
                .errorCode("CONFIRM_REQUIRED")
                .message("Capability requires confirmation before execution")
                .build();
    }

    public static CapabilityResult failure(String name, String errorCode, String message) {
        return CapabilityResult.builder()
                .name(name)
                .status(CapabilityStatus.FAILURE)
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    public boolean isSuccess() {
        return status == CapabilityStatus.SUCCESS;
    }

}
