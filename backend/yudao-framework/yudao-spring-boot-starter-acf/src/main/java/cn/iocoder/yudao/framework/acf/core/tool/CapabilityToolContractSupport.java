package cn.iocoder.yudao.framework.acf.core.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具调用的通用控制字段契约。
 *
 * <p>ACF 核心能力的业务参数不包含幂等键、确认令牌等协议控制字段，但 Agent
 * 必须能在工具 schema 中看到并填写这些字段。因此导出工具时由本类增强 schema，
 * 调用工具时再把控制字段从业务参数中剥离。</p>
 *
 * @author bujidao
 */
public class CapabilityToolContractSupport {

    public static final String CLIENT_REQUEST_ID = "clientRequestId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String CONFIRMATION_TOKEN = "confirmationToken";

    private static final int MAX_CONTROL_VALUE_LENGTH = 256;

    public Map<String, Object> enrichInputSchema(Map<String, Object> inputSchema, boolean idempotencyRequired) {
        Map<String, Object> schema = deepCopyMap(inputSchema);
        schema.putIfAbsent("type", "object");
        Map<String, Object> properties = ensureObjectMap(schema, "properties");
        addControlProperty(properties, CLIENT_REQUEST_ID,
                "Optional request correlation id for traceability. Reuse it across retries of the same user intent.");
        addControlProperty(properties, IDEMPOTENCY_KEY,
                "Required for side-effecting or confirmation-required capabilities. Generate a stable unique key for this business intent and reuse it on retries.");
        addControlProperty(properties, CONFIRMATION_TOKEN,
                "Confirmation token returned by the approval flow. Provide it only when retrying after confirmation.");
        if (idempotencyRequired) {
            LinkedHashSet<String> required = new LinkedHashSet<>(readRequired(schema));
            required.add(IDEMPOTENCY_KEY);
            schema.put("required", new ArrayList<>(required));
        }
        return Collections.unmodifiableMap(schema);
    }

    public CapabilityToolInvocationInput resolveInvocationInput(Map<String, Object> rawInput) {
        Map<String, Object> businessArguments = new LinkedHashMap<>(rawInput == null ? Map.of() : rawInput);
        String clientRequestId = removeOptionalString(businessArguments, CLIENT_REQUEST_ID);
        String idempotencyKey = removeOptionalString(businessArguments, IDEMPOTENCY_KEY);
        String confirmationToken = removeOptionalString(businessArguments, CONFIRMATION_TOKEN);
        return CapabilityToolInvocationInput.builder()
                .businessArguments(businessArguments)
                .clientRequestId(clientRequestId)
                .idempotencyKey(idempotencyKey)
                .confirmationToken(confirmationToken)
                .build();
    }

    private void addControlProperty(Map<String, Object> properties, String name, String description) {
        properties.putIfAbsent(name, controlProperty(description));
    }

    private Map<String, Object> controlProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return Collections.unmodifiableMap(property);
    }

    private List<String> readRequired(Map<String, Object> schema) {
        Object required = schema.get("required");
        if (required instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String removeOptionalString(Map<String, Object> arguments, String key) {
        Object value = arguments.remove(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Tool control argument must be a string: " + key);
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_CONTROL_VALUE_LENGTH) {
            throw new IllegalArgumentException("Tool control argument is too long: " + key);
        }
        return normalized;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> target.put(key, deepCopyValue(value)));
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> target = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> target.put(String.valueOf(key), deepCopyValue(nestedValue)));
            return target;
        }
        if (value instanceof List<?> list) {
            List<Object> target = new ArrayList<>(list.size());
            list.forEach(item -> target.add(deepCopyValue(item)));
            return target;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureObjectMap(Map<String, Object> schema, String key) {
        Object value = schema.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> target = new LinkedHashMap<>();
            map.forEach((mapKey, mapValue) -> target.put(String.valueOf(mapKey), deepCopyValue(mapValue)));
            schema.put(key, target);
            return target;
        }
        Map<String, Object> target = new LinkedHashMap<>();
        schema.put(key, target);
        return target;
    }

}
