package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable capability declaration and schema snapshot.
 *
 * @author bujidao
 */
@Getter
public final class CapabilityDefinition {

    private final String name;
    private final String title;
    private final String description;
    private final String category;
    private final List<String> permissions;
    private final CapabilityPermissionMode permissionMode;
    private final CapabilityRiskLevel riskLevel;
    private final boolean sideEffect;
    private final boolean confirmationRequired;
    private final String version;
    private final int timeoutMs;
    private final Type argumentType;
    private final Type returnType;
    private final Map<String, Object> inputSchema;
    private final Map<String, Object> outputSchema;

    @Builder(toBuilder = true)
    private CapabilityDefinition(String name, String title, String description, String category,
                                 List<String> permissions, CapabilityPermissionMode permissionMode,
                                 CapabilityRiskLevel riskLevel, boolean sideEffect,
                                 boolean confirmationRequired, String version, int timeoutMs,
                                 Type argumentType, Type returnType, Map<String, Object> inputSchema,
                                 Map<String, Object> outputSchema) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.category = category;
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        this.permissionMode = permissionMode;
        this.riskLevel = riskLevel;
        this.sideEffect = sideEffect;
        this.confirmationRequired = confirmationRequired;
        this.version = version;
        this.timeoutMs = timeoutMs;
        this.argumentType = argumentType;
        this.returnType = returnType;
        this.inputSchema = immutableMap(inputSchema);
        this.outputSchema = immutableMap(outputSchema);
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
