package cn.iocoder.yudao.module.mcp.framework.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 ACF 的 JSON Schema 调整为 MCP Tool 所需的对象形态。
 *
 * @author bujidao
 */
public final class McpSchemaAdapter {

    public static final String INPUT_VALUE_PROPERTY = "value";
    public static final String OUTPUT_RESULT_PROPERTY = "result";

    private McpSchemaAdapter() {
    }

    public static Map<String, Object> adaptInputSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty() || isVoidSchema(schema)) {
            return emptyObjectSchema();
        }
        Map<String, Object> copy = deepCopyMap(schema);
        if (isObjectSchema(copy)) {
            return copy;
        }
        return wrapSchema(INPUT_VALUE_PROPERTY, copy);
    }

    public static Map<String, Object> adaptOutputSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty() || isVoidSchema(schema)) {
            return emptyObjectSchema();
        }
        Map<String, Object> copy = deepCopyMap(schema);
        if (isObjectSchema(copy)) {
            return copy;
        }
        return wrapSchema(OUTPUT_RESULT_PROPERTY, copy);
    }

    private static boolean isObjectSchema(Map<String, Object> schema) {
        return "object".equals(schema.get("type"));
    }

    private static boolean isVoidSchema(Map<String, Object> schema) {
        return "null".equals(schema.get("type"));
    }

    private static Map<String, Object> emptyObjectSchema() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", new LinkedHashMap<>());
        result.put("additionalProperties", false);
        return result;
    }

    private static Map<String, Object> wrapSchema(String propertyName, Map<String, Object> schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(propertyName, schema);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", properties);
        result.put("required", List.of(propertyName));
        result.put("additionalProperties", false);
        return result;
    }

    private static Map<String, Object> deepCopyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), deepCopyValue(value)));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(deepCopyValue(item)));
            return copy;
        }
        return value;
    }

}
