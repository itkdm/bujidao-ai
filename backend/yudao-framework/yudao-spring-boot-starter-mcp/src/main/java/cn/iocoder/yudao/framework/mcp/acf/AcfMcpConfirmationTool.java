package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 侧确认 ACF challenge 的合成工具。
 *
 * @author bujidao
 */
public class AcfMcpConfirmationTool {

    public static final String NAME = "acf.confirm";

    private static final String CHALLENGE_ID = "challengeId";
    private static final String CONFIRM_REMARK = "confirmRemark";

    private final CapabilityConfirmationService confirmationService;

    public AcfMcpConfirmationTool(CapabilityConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    public McpSchema.Tool tool() {
        // Modern Tool.builder(name, inputSchema)：name 与 inputSchema 是必填构造参数。
        return McpSchema.Tool.builder(NAME, inputSchema())
                .title("Confirm ACF capability")
                .description("Confirm a previously challenged ACF capability invocation")
                .outputSchema(outputSchema())
                .annotations(McpSchema.ToolAnnotations.builder()
                        .title("Confirm ACF capability")
                        .readOnlyHint(false)
                        .idempotentHint(false)
                        .build())
                .meta(Map.of(AcfMcpToolProtocolMetadata.CONFIRMATION_REQUIRED, false,
                        AcfMcpToolProtocolMetadata.IDEMPOTENCY_REQUIRED, false))
                .build();
    }

    public McpSyncResponse<CallToolOutcome> handle(McpRequestContext requestContext,
                                                   McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        String challengeId = optionalString(arguments.get(CHALLENGE_ID));
        if (!StringUtils.hasText(challengeId)) {
            return McpSyncResponse.result(errorResult(AcfCapabilityErrorCodes.BAD_REQUEST,
                    "challengeId is required"));
        }
        String confirmRemark = optionalString(arguments.get(CONFIRM_REMARK));
        CapabilityContext context = AcfMcpContextSupport.fromTransport(requestContext.transportContext(), null);
        return McpSyncResponse.result(confirmWithTenantContext(context, challengeId, confirmRemark));
    }

    private McpSchema.CallToolResult confirmWithTenantContext(CapabilityContext context, String challengeId,
                                                              String confirmRemark) {
        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            setTenantId(context.getTenantId());
            CapabilityConfirmationToken token = confirmationService.confirm(challengeId, context, confirmRemark);
            return successResult(token);
        } catch (IllegalArgumentException exception) {
            return errorResult(AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID, exception.getMessage());
        } catch (RuntimeException exception) {
            return errorResult(AcfCapabilityErrorCodes.CONFIRMATION_ERROR, "Capability confirmation failed");
        } finally {
            setTenantId(previousTenantId);
        }
    }

    private static McpSchema.CallToolResult successResult(CapabilityConfirmationToken token) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("challengeId", token.getChallengeId());
        structuredContent.put("confirmationToken", token.getConfirmationToken());
        structuredContent.put("expiresAt", format(token.getExpiresAt()));
        structuredContent.put("nextAction", "Retry the original tool with the same idempotencyKey and this confirmationToken.");
        return McpSchema.CallToolResult.builder()
                .addContent(TextContent.builder(
                        "Confirmation accepted. Retry the original tool with the same idempotencyKey and the returned confirmationToken.")
                        .build())
                .structuredContent(structuredContent)
                .isError(false)
                .build();
    }

    private static McpSchema.CallToolResult errorResult(String errorCode, String message) {
        // Modern TextContent.builder(text) 要求 text 非空，异常 message 可能为 null 时给默认值。
        String text = message == null ? "Capability confirmation failed" : message;
        return McpSchema.CallToolResult.builder()
                .addContent(TextContent.builder(text).build())
                .meta(Map.of(AcfMcpToolProtocolMetadata.STATUS, "FAILURE",
                        AcfMcpToolProtocolMetadata.ERROR_CODE, errorCode,
                        AcfMcpToolProtocolMetadata.RETRYABLE, false))
                .isError(true)
                .build();
    }

    private static Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CHALLENGE_ID, Map.of("type", "string",
                "description", "The challengeId returned by the original ACF tool call."));
        properties.put(CONFIRM_REMARK, Map.of("type", "string",
                "description", "Optional confirmation note."));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(CHALLENGE_ID));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> outputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("challengeId", Map.of("type", "string"));
        properties.put("confirmationToken", Map.of("type", "string"));
        properties.put("expiresAt", Map.of("type", "string", "format", "date-time"));
        properties.put("nextAction", Map.of("type", "string"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("challengeId", "confirmationToken", "expiresAt", "nextAction"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static void setTenantId(Long tenantId) {
        if (tenantId == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.setTenantId(tenantId);
        }
    }

}
