package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 幂等执行权获取结果
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityIdempotencyCheck {

    private final CapabilityIdempotencyStatus status;
    private final CapabilityResult replayResult;
    private final String errorCode;
    private final String reason;

    public static CapabilityIdempotencyCheck acquired() {
        return CapabilityIdempotencyCheck.builder()
                .status(CapabilityIdempotencyStatus.ACQUIRED)
                .build();
    }

    public static CapabilityIdempotencyCheck replayed(CapabilityResult replayResult) {
        return CapabilityIdempotencyCheck.builder()
                .status(CapabilityIdempotencyStatus.REPLAYED)
                .replayResult(replayResult)
                .build();
    }

    public static CapabilityIdempotencyCheck conflict(String errorCode, String reason) {
        return CapabilityIdempotencyCheck.builder()
                .status(CapabilityIdempotencyStatus.CONFLICT)
                .errorCode(errorCode)
                .reason(reason)
                .build();
    }

}
