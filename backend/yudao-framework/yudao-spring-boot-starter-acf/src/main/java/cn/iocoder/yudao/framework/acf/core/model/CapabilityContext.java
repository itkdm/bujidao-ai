package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 一次能力调用的治理上下文
 *
 * 上下文只承载调用方信息，不直接读取 Security、Tenant 等框架静态上下文，
 * 由 REST、Agent、MCP 等调用入口根据自身认证环境负责构建。
 *
 * @author bujidao
 */
@Getter
public final class CapabilityContext {

    private final Long userId;
    private final Long tenantId;
    private final String source;
    private final CapabilityConsumerType consumerType;
    private final String consumerId;
    private final Map<String, Object> attributes;

    @Builder
    private CapabilityContext(Long userId, Long tenantId, String source, CapabilityConsumerType consumerType,
                              String consumerId, Map<String, Object> attributes) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.source = source;
        this.consumerType = consumerType;
        this.consumerId = consumerId;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static CapabilityContext empty() {
        return CapabilityContext.builder().build();
    }

}
