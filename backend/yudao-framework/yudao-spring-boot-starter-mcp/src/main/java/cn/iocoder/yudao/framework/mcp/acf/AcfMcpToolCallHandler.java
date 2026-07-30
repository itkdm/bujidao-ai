package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 MCP Tool 调用适配到 ACF 的统一工具执行入口。
 *
 * @author bujidao
 */
public class AcfMcpToolCallHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcfMcpToolCallHandler.class);
    private static final String SOURCE = "MCP";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_MESSAGE = "Capability invocation failed";

    private final CapabilityToolInvoker capabilityToolInvoker;
    private final McpJsonMapper jsonMapper;
    private final AcfMcpStructuredContentNormalizer structuredContentNormalizer;

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker, McpJsonMapper jsonMapper) {
        this(capabilityToolInvoker, jsonMapper, new AcfMcpStructuredContentNormalizer());
    }

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker, McpJsonMapper jsonMapper,
                                 AcfMcpStructuredContentNormalizer structuredContentNormalizer) {
        this.capabilityToolInvoker = capabilityToolInvoker;
        this.jsonMapper = jsonMapper;
        this.structuredContentNormalizer = structuredContentNormalizer;
    }

    public McpSchema.CallToolResult handle(McpTransportContext transportContext,
                                           CapabilityToolDescriptor descriptor,
                                           McpSchema.CallToolRequest request) {
        AcfMcpToolProtocolMetadata.ToolCallControl control;
        try {
            control = AcfMcpToolProtocolMetadata.readCallControl(request.meta());
        } catch (IllegalArgumentException exception) {
            return errorResult(CapabilityStatus.FAILURE, "BAD_REQUEST", exception.getMessage(),
                    false, null, null);
        }
        try {
            CapabilityContext context = createContext(transportContext, control.clientRequestId());
            CapabilityToolCall call = CapabilityToolCall.builder()
                    .capabilityName(descriptor.getCapabilityName())
                    .arguments(adaptArguments(descriptor, request.arguments()))
                    .context(context)
                    .idempotencyKey(control.idempotencyKey())
                    .confirmationToken(control.confirmationToken())
                    .build();
            return invokeWithTenantContext(descriptor, call, context.getTenantId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Unexpected MCP tool invocation failure: capability={}, exceptionType={}",
                    descriptor.getCapabilityName(), exception.getClass().getName());
            return errorResult(CapabilityStatus.FAILURE, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE,
                    false, null, null);
        }
    }

    private McpSchema.CallToolResult invokeWithTenantContext(CapabilityToolDescriptor descriptor,
                                                             CapabilityToolCall call, Long tenantId) {
        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            setTenantId(tenantId);
            CapabilityResult result = capabilityToolInvoker.invoke(call);
            return adaptResult(descriptor, result);
        } finally {
            setTenantId(previousTenantId);
        }
    }

    private static void setTenantId(Long tenantId) {
        if (tenantId == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.setTenantId(tenantId);
        }
    }

    private static CapabilityContext createContext(McpTransportContext transportContext, String clientRequestId) {
        return CapabilityContext.builder()
                .userId(contextValue(transportContext, McpTransportContextKeys.USER_ID, Long.class))
                .tenantId(contextValue(transportContext, McpTransportContextKeys.TENANT_ID, Long.class))
                .source(SOURCE)
                .consumerType(CapabilityConsumerType.MCP)
                .consumerId(contextValue(transportContext, McpTransportContextKeys.CONSUMER_ID, String.class))
                .clientRequestId(clientRequestId)
                .attributes(contextAttributes(transportContext))
                .build();
    }

    private static Map<String, Object> contextAttributes(McpTransportContext transportContext) {
        String clientId = contextValue(transportContext, McpTransportContextKeys.CLIENT_ID, String.class);
        if (clientId == null || clientId.isBlank()) {
            return Map.of();
        }
        return Map.of("oauthClientId", clientId);
    }

    private static <T> T contextValue(McpTransportContext context, String key, Class<T> type) {
        if (context == null) {
            return null;
        }
        Object value = context.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static Object adaptArguments(CapabilityToolDescriptor descriptor, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (isObjectSchema(descriptor.getInputSchema())) {
            return safeArguments;
        }
        return safeArguments.get(McpSchemaAdapter.INPUT_VALUE_PROPERTY);
    }

    private McpSchema.CallToolResult adaptResult(CapabilityToolDescriptor descriptor,
                                                 CapabilityResult result) {
        if (result == null) {
            return errorResult(CapabilityStatus.FAILURE, null, "Capability invocation failed",
                    false, null, null);
        }
        if (result.getStatus() != CapabilityStatus.SUCCESS) {
            CapabilityConfirmationChallenge challenge = result.getData() instanceof CapabilityConfirmationChallenge item
                    ? item : null;
            return errorResult(result.getStatus(), result.getErrorCode(),
                    defaultMessage(result.getMessage(), "Capability invocation failed"),
                    result.isRetryable(), result.getTraceId(), challenge);
        }
        Object structuredContent = structuredContentNormalizer.normalize(
                adaptStructuredContent(descriptor, result.getData()));
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .addTextContent(serializeStructuredContent(structuredContent))
                .structuredContent(structuredContent)
                .meta(resultMetadata(result))
                .isError(false);
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            builder.addTextContent(result.getMessage());
        }
        return builder.build();
    }

    private String serializeStructuredContent(Object structuredContent) {
        try {
            return jsonMapper.writeValueAsString(structuredContent);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize MCP structured content", exception);
        }
    }

    private static McpSchema.CallToolResult errorResult(CapabilityStatus status, String errorCode,
                                                        String message, boolean retryable,
                                                        String traceId,
                                                        CapabilityConfirmationChallenge challenge) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(AcfMcpToolProtocolMetadata.STATUS,
                status == null ? CapabilityStatus.FAILURE.name() : status.name());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, AcfMcpToolProtocolMetadata.TRACE_ID, traceId);
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, AcfMcpToolProtocolMetadata.ERROR_CODE, errorCode);
        metadata.put(AcfMcpToolProtocolMetadata.RETRYABLE, retryable);
        if (challenge != null) {
            metadata.put(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE, confirmationChallenge(challenge));
        }
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .meta(metadata)
                .isError(true)
                .build();
    }

    private static Map<String, Object> resultMetadata(CapabilityResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(AcfMcpToolProtocolMetadata.STATUS, result.getStatus().name());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, AcfMcpToolProtocolMetadata.TRACE_ID, result.getTraceId());
        metadata.put(AcfMcpToolProtocolMetadata.RETRYABLE, result.isRetryable());
        return metadata;
    }

    private static Map<String, Object> confirmationChallenge(CapabilityConfirmationChallenge challenge) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "challengeId", challenge.getChallengeId());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "capabilityName", challenge.getCapabilityName());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "capabilityVersion", challenge.getCapabilityVersion());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "riskLevel",
                challenge.getRiskLevel() == null ? null : challenge.getRiskLevel().name());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "expiresAt",
                challenge.getExpiresAt() == null ? null : challenge.getExpiresAt().toString());
        return metadata;
    }

    private static Object adaptStructuredContent(CapabilityToolDescriptor descriptor, Object data) {
        Map<String, Object> outputSchema = descriptor.getOutputSchema();
        if (outputSchema == null || outputSchema.isEmpty() || "null".equals(outputSchema.get("type"))) {
            return Map.of();
        }
        if (isObjectSchema(outputSchema)) {
            return data == null ? Map.of() : data;
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY, data);
        return wrapped;
    }

    private static boolean isObjectSchema(Map<String, Object> schema) {
        return schema != null && "object".equals(schema.get("type"));
    }

    private static String defaultMessage(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }

}
