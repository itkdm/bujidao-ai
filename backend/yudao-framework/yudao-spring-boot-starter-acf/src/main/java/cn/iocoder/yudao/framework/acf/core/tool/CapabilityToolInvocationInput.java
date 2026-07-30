package cn.iocoder.yudao.framework.acf.core.tool;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协议适配层解析后的工具调用输入。
 *
 * <p>业务参数和调用控制字段在这里完成隔离，避免幂等键、确认令牌等协议控制信息
 * 被传入业务 DTO。</p>
 *
 * @author bujidao
 */
@Getter
public final class CapabilityToolInvocationInput {

    private final Map<String, Object> businessArguments;
    private final String clientRequestId;
    private final String idempotencyKey;
    private final String confirmationToken;

    @Builder
    private CapabilityToolInvocationInput(Map<String, Object> businessArguments, String clientRequestId,
                                          String idempotencyKey, String confirmationToken) {
        this.businessArguments = immutableArguments(businessArguments);
        this.clientRequestId = clientRequestId;
        this.idempotencyKey = idempotencyKey;
        this.confirmationToken = confirmationToken;
    }

    public static CapabilityToolInvocationInput empty() {
        return CapabilityToolInvocationInput.builder()
                .businessArguments(Map.of())
                .build();
    }

    private static Map<String, Object> immutableArguments(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

}
