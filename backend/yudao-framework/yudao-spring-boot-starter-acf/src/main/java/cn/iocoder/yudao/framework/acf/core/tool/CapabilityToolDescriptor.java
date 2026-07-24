package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向 Agent 与外部协议适配器的能力工具描述
 *
 * 该模型只表达协议无关的能力契约。具体协议如何转换工具名称、包装参数或增加控制字段，
 * 由对应适配器负责，避免 ACF 核心层绑定 Spring AI、MCP 或 OpenAI 数据结构。
 *
 * @author bujidao
 */
@Getter
public final class CapabilityToolDescriptor {

    private final String capabilityName;
    private final String version;
    private final String title;
    private final String description;
    private final String category;
    private final Map<String, Object> inputSchema;
    private final Map<String, Object> outputSchema;
    private final CapabilityPermissionMode permissionMode;
    private final List<String> permissions;
    private final CapabilityRiskLevel riskLevel;
    private final boolean sideEffect;
    private final boolean confirmationRequired;

    @Builder
    private CapabilityToolDescriptor(String capabilityName, String version, String title, String description,
                                     String category, Map<String, Object> inputSchema,
                                     Map<String, Object> outputSchema, CapabilityPermissionMode permissionMode,
                                     List<String> permissions, CapabilityRiskLevel riskLevel,
                                     boolean sideEffect, boolean confirmationRequired) {
        this.capabilityName = capabilityName;
        this.version = version;
        this.title = title;
        this.description = description;
        this.category = category;
        this.inputSchema = immutableMap(inputSchema);
        this.outputSchema = immutableMap(outputSchema);
        this.permissionMode = permissionMode;
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        this.riskLevel = riskLevel;
        this.sideEffect = sideEffect;
        this.confirmationRequired = confirmationRequired;
    }

    static CapabilityToolDescriptor from(CapabilityDefinition definition) {
        return CapabilityToolDescriptor.builder()
                .capabilityName(definition.getName())
                .version(definition.getVersion())
                .title(definition.getTitle())
                .description(definition.getDescription())
                .category(definition.getCategory())
                .inputSchema(definition.getInputSchema())
                .outputSchema(definition.getOutputSchema())
                .permissionMode(definition.getPermissionMode())
                .permissions(definition.getPermissions())
                .riskLevel(definition.getRiskLevel())
                .sideEffect(definition.isSideEffect())
                .confirmationRequired(definition.isConfirmationRequired())
                .build();
    }

    /**
     * 副作用能力和需要确认的能力都必须由调用入口提供稳定幂等键。
     */
    public boolean isIdempotencyRequired() {
        return sideEffect || confirmationRequired;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, immutableValue(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(immutableValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

}
