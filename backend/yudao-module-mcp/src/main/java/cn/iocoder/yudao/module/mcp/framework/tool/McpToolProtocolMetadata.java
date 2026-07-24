package cn.iocoder.yudao.module.mcp.framework.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * bujidao MCP Tool 扩展元数据契约。
 *
 * 控制字段通过 MCP 的 {@code _meta} 传递，不混入业务参数，避免改变 ACF 能力契约。
 *
 * @author bujidao
 */
public final class McpToolProtocolMetadata {

    public static final String CAPABILITY_VERSION = "bujidao.ai/capabilityVersion";
    public static final String RISK_LEVEL = "bujidao.ai/riskLevel";
    public static final String IDEMPOTENCY_REQUIRED = "bujidao.ai/idempotencyRequired";
    public static final String CONFIRMATION_REQUIRED = "bujidao.ai/confirmationRequired";

    public static final String IDEMPOTENCY_KEY = "bujidao.ai/idempotencyKey";
    public static final String CONFIRMATION_TOKEN = "bujidao.ai/confirmationToken";
    public static final String CLIENT_REQUEST_ID = "bujidao.ai/clientRequestId";

    public static final String TRACE_ID = "bujidao.ai/traceId";
    public static final String STATUS = "bujidao.ai/status";
    public static final String ERROR_CODE = "bujidao.ai/errorCode";
    public static final String RETRYABLE = "bujidao.ai/retryable";
    public static final String CONFIRMATION_CHALLENGE = "bujidao.ai/confirmationChallenge";

    private static final int MAX_CONTROL_VALUE_LENGTH = 256;

    private McpToolProtocolMetadata() {
    }

    static ToolCallControl readCallControl(Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        return new ToolCallControl(
                readOptionalString(safeMetadata, IDEMPOTENCY_KEY),
                readOptionalString(safeMetadata, CONFIRMATION_TOKEN),
                readOptionalString(safeMetadata, CLIENT_REQUEST_ID));
    }

    static Map<String, Object> toolMetadata(String capabilityVersion, String riskLevel,
                                            boolean idempotencyRequired, boolean confirmationRequired) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, CAPABILITY_VERSION, capabilityVersion);
        putIfPresent(metadata, RISK_LEVEL, riskLevel);
        metadata.put(IDEMPOTENCY_REQUIRED, idempotencyRequired);
        metadata.put(CONFIRMATION_REQUIRED, confirmationRequired);
        return metadata;
    }

    private static String readOptionalString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("MCP control metadata must be a string: " + key);
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_CONTROL_VALUE_LENGTH) {
            throw new IllegalArgumentException("MCP control metadata is too long: " + key);
        }
        return normalized;
    }

    static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    record ToolCallControl(String idempotencyKey, String confirmationToken, String clientRequestId) {
    }

}
