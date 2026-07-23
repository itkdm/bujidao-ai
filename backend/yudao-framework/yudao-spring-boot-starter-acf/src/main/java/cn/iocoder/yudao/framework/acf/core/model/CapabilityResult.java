package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力执行结果
 *
 * @author bujidao
 */
@Getter
public final class CapabilityResult {

    private final String traceId;
    private final String name;
    private final CapabilityStatus status;
    private final Object data;
    private final String errorCode;
    private final String message;
    private final boolean retryable;
    private final List<CapabilityEvidence> evidence;
    private final List<CapabilityNextAction> suggestedNextActions;

    @Builder(toBuilder = true)
    private CapabilityResult(String traceId, String name, CapabilityStatus status, Object data, String errorCode,
                             String message, boolean retryable, List<CapabilityEvidence> evidence,
                             List<CapabilityNextAction> suggestedNextActions) {
        this.traceId = traceId;
        this.name = name;
        this.status = status;
        this.data = data;
        this.errorCode = errorCode;
        this.message = message;
        this.retryable = retryable;
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.suggestedNextActions = suggestedNextActions == null ? List.of() : List.copyOf(suggestedNextActions);
    }

    public static CapabilityResult success(String name, Object data) {
        return success(name, data, null);
    }

    public static CapabilityResult success(String name, Object data, String message) {
        return CapabilityResult.builder()
                .name(name)
                .status(CapabilityStatus.SUCCESS)
                .data(data)
                .message(message)
                .build();
    }

    public static CapabilityResult success(Object data, String message) {
        return success(null, data, message);
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
        return failure(name, errorCode, message, false);
    }

    public static CapabilityResult failure(String name, String errorCode, String message, boolean retryable) {
        return CapabilityResult.builder()
                .name(name)
                .status(CapabilityStatus.FAILURE)
                .errorCode(errorCode)
                .message(message)
                .retryable(retryable)
                .build();
    }

    public static CapabilityResult failure(String errorCode, String message, boolean retryable) {
        return failure(null, errorCode, message, retryable);
    }

    public static CapabilityResult denied(String name, String errorCode, String message) {
        return CapabilityResult.builder()
                .name(name)
                .status(CapabilityStatus.DENIED)
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    public CapabilityResult withTraceId(String traceId) {
        return toBuilder().traceId(traceId).build();
    }

    public CapabilityResult withName(String name) {
        return toBuilder().name(name).build();
    }

    public CapabilityResult withEvidence(CapabilityEvidence item) {
        if (item == null) {
            return this;
        }
        List<CapabilityEvidence> items = new ArrayList<>(evidence);
        items.add(item);
        return toBuilder().evidence(items).build();
    }

    public CapabilityResult withSuggestedNextAction(CapabilityNextAction action) {
        if (action == null) {
            return this;
        }
        List<CapabilityNextAction> actions = new ArrayList<>(suggestedNextActions);
        actions.add(action);
        return toBuilder().suggestedNextActions(actions).build();
    }

    public boolean isSuccess() {
        return status == CapabilityStatus.SUCCESS;
    }

}
