package cn.iocoder.yudao.module.mcp.framework.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.module.mcp.framework.security.McpTransportContextKeys;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 MCP Tool 调用适配到 ACF 的统一工具执行入口。
 *
 * @author bujidao
 */
public class AcfMcpToolCallHandler {

    private static final String SOURCE = "MCP";

    private final CapabilityToolInvoker capabilityToolInvoker;

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker) {
        this.capabilityToolInvoker = capabilityToolInvoker;
    }

    public McpSchema.CallToolResult handle(McpTransportContext transportContext,
                                           CapabilityToolDescriptor descriptor,
                                           McpSchema.CallToolRequest request) {
        CapabilityToolCall call = CapabilityToolCall.builder()
                .capabilityName(descriptor.getCapabilityName())
                .arguments(adaptArguments(descriptor, request.arguments()))
                .context(createContext(transportContext))
                .build();
        CapabilityResult result = capabilityToolInvoker.invoke(call);
        return adaptResult(descriptor, result);
    }

    private static CapabilityContext createContext(McpTransportContext transportContext) {
        return CapabilityContext.builder()
                .userId(contextValue(transportContext, McpTransportContextKeys.USER_ID, Long.class))
                .tenantId(contextValue(transportContext, McpTransportContextKeys.TENANT_ID, Long.class))
                .source(SOURCE)
                .consumerType(CapabilityConsumerType.MCP)
                .consumerId(contextValue(transportContext, McpTransportContextKeys.CONSUMER_ID, String.class))
                .build();
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

    private static McpSchema.CallToolResult adaptResult(CapabilityToolDescriptor descriptor,
                                                        CapabilityResult result) {
        if (result == null) {
            return errorResult("Capability invocation failed");
        }
        if (result.getStatus() != CapabilityStatus.SUCCESS) {
            return errorResult(defaultMessage(result.getMessage(), "Capability invocation failed"));
        }
        Object structuredContent = adaptStructuredContent(descriptor, result.getData());
        return McpSchema.CallToolResult.builder()
                .addTextContent(defaultMessage(result.getMessage(), "Capability executed successfully"))
                .structuredContent(structuredContent)
                .isError(false)
                .build();
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
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
