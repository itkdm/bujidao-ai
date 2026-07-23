package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 一次能力调用的治理上下文
 *
 * 上下文只承载调用方信息，不直接读取 Security、Tenant 等框架静态上下文，
 * 由 REST、Agent、MCP 等可信调用入口根据自身认证环境负责构建，
 * 不得直接信任外部请求传入的 userId、tenantId 等身份字段。
 *
 * @author bujidao
 */
@Getter
public final class CapabilityContext {

    private final String traceId;
    private final Long userId;
    private final Long tenantId;
    private final String source;
    private final CapabilityConsumerType consumerType;
    private final String consumerId;
    private final String clientRequestId;
    private final Map<String, Object> attributes;

    @Builder(toBuilder = true)
    private CapabilityContext(String traceId, Long userId, Long tenantId, String source,
                              CapabilityConsumerType consumerType, String consumerId, String clientRequestId,
                              Map<String, Object> attributes) {
        this.traceId = traceId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.source = source;
        this.consumerType = consumerType;
        this.consumerId = consumerId;
        this.clientRequestId = clientRequestId;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static CapabilityContext empty() {
        return CapabilityContext.builder().build();
    }

    public CapabilityContext withTraceId(String traceId) {
        return toBuilder().traceId(traceId).build();
    }

}
