package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvocationInput;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.server.InputResponses;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 MCP Tool 调用适配到 ACF 的统一工具执行入口。
 *
 * <p>需要确认的能力走 MRTR 标准确认（MCP 2026-07-28）：客户端不再先调一个独立的
 * 确认工具拿 token。第一轮调用返回 {@link McpSchema.InputRequiredResult}，用表单
 * elicitation 请用户确认；客户端带 {@code inputResponses} 重试时，handler 先调
 * {@link CapabilityConfirmationService#confirm(String, CapabilityContext, String)}
 * 把用户的接受换成确认 token，再用该 token 正常执行工具调用。确认 token 的签发与
 * 消费仍走 ACF 核心的 challenge 机制（见 {@code CapabilityExecutor}），本类只负责
 * MCP 侧的 elicitation 往返，不自建第二套确认状态。</p>
 *
 * <p>challenge 的引用（challengeId）放在签名的 {@code requestState} 里随
 * {@code InputRequiredResult} 返回：SDK 在发送前用 HMAC-SHA256 签名（默认 10 分钟
 * 过期，绑定 tools/call 方法与工具名），重试时先验签、开封，篡改或过期的 state 在
 * handler 运行前就被 SDK 以 invalid params 拒绝。注意签名不是加密：payload 是
 * base64url 编码的明文 JSON，客户端能读出 challengeId 与用户 id，但改不了；
 * 所以 state 里只放 challengeId，不放任何秘密。用户、租户、幂等键、请求摘要的
 * 绑定不在 state 里，而在服务端 challenge 记录本身（创建时写入，confirm 与
 * verifyAndConsumeToken 时逐项比对），所以 state 里只放 challengeId 即可。</p>
 *
 * @author bujidao
 */
public class AcfMcpToolCallHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcfMcpToolCallHandler.class);
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_MESSAGE = "Capability invocation failed";

    /**
     * 确认 elicitation 在 {@code inputResponses} 里用的 key。服务端与客户端按这个
     * key 对齐一次往返；换 key 即换一轮确认。
     */
    public static final String CONFIRMATION_INPUT_KEY = "acf.confirmation";

    /**
     * 确认表单里唯一的字段：确认备注（可选）。challenge 需要的其它信息都不由用户
     * 填：challengeId 在签名的 requestState 里，接受 / 拒绝本身就是
     * {@link McpSchema.ElicitResult} 的 action（accept / decline / cancel），
     * 用户、租户、幂等键、请求摘要由服务端 challenge 记录绑定。
     */
    public static final String CONFIRM_REMARK_FIELD = "confirmRemark";

    /**
     * 确认备注的最大长度，与确认服务对备注的截断上限一致（超过部分由服务截断，
     * 这里只作表单提示，不在 handler 侧拒绝）。
     */
    private static final int CONFIRM_REMARK_MAX_LENGTH = 512;

    private final CapabilityToolInvoker capabilityToolInvoker;
    private final McpJsonMapper jsonMapper;
    private final AcfMcpStructuredContentNormalizer structuredContentNormalizer;
    private final CapabilityToolContractSupport toolContractSupport;
    private final CapabilityConfirmationService confirmationService;

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker, McpJsonMapper jsonMapper) {
        this(capabilityToolInvoker, jsonMapper, new AcfMcpStructuredContentNormalizer(),
                new CapabilityToolContractSupport(), null);
    }

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker, McpJsonMapper jsonMapper,
                                 AcfMcpStructuredContentNormalizer structuredContentNormalizer) {
        this(capabilityToolInvoker, jsonMapper, structuredContentNormalizer,
                new CapabilityToolContractSupport(), null);
    }

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker, McpJsonMapper jsonMapper,
                                 AcfMcpStructuredContentNormalizer structuredContentNormalizer,
                                 CapabilityToolContractSupport toolContractSupport) {
        this(capabilityToolInvoker, jsonMapper, structuredContentNormalizer, toolContractSupport, null);
    }

    public AcfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker, McpJsonMapper jsonMapper,
                                 AcfMcpStructuredContentNormalizer structuredContentNormalizer,
                                 CapabilityToolContractSupport toolContractSupport,
                                 CapabilityConfirmationService confirmationService) {
        this.capabilityToolInvoker = capabilityToolInvoker;
        this.jsonMapper = jsonMapper;
        this.structuredContentNormalizer = structuredContentNormalizer;
        this.toolContractSupport = toolContractSupport;
        this.confirmationService = confirmationService;
    }

    /**
     * Modern sync tool handler：第一个参数是 {@link McpRequestContext}，身份从
     * {@code requestContext.transportContext()} 取，控制字段从 {@code requestContext.meta()}
     *（即请求 _meta）取；返回值包一层 {@link McpSyncResponse#result(Object)}。
     *
     * <p>带签名 {@code requestState} 的请求是 MRTR 重试：SDK 已在 handler 运行前
     * 验签开封，{@code request.requestState()} 拿到的是原始 challengeId。重试时
     * 不重新创建 challenge、不重新执行，先读 elicitation 答案再决定确认或拒绝。</p>
     */
    public McpSyncResponse<CallToolOutcome> handle(McpRequestContext requestContext,
                                                   CapabilityToolDescriptor descriptor,
                                                   McpSchema.CallToolRequest request) {
        AcfMcpToolProtocolMetadata.ToolCallControl control;
        try {
            control = AcfMcpToolProtocolMetadata.readCallControl(requestContext.meta());
        } catch (IllegalArgumentException exception) {
            return McpSyncResponse.result(errorResult(CapabilityStatus.FAILURE, "BAD_REQUEST",
                    exception.getMessage(), false, null, null));
        }
        CapabilityToolInvocationInput invocationInput;
        CapabilityContext context;
        try {
            invocationInput = toolContractSupport.resolveInvocationInput(request.arguments());
            context = AcfMcpContextSupport.fromTransport(requestContext.transportContext(),
                    firstPresent(invocationInput.getClientRequestId(), control.clientRequestId()));
        } catch (IllegalArgumentException exception) {
            return McpSyncResponse.result(errorResult(CapabilityStatus.FAILURE, "BAD_REQUEST",
                    exception.getMessage(), false, null, null));
        }
        String challengeId = blankToNull(request.requestState());
        if (challengeId != null) {
            // 故意放在 try/catch 之外：答案格式错误时 InputResponses.get 抛出的
            // McpException（-32602 invalid params）必须原样到达服务端框架，
            // 不能被下面兜底的 RuntimeException 分支压成内部错误。
            McpSchema.ElicitResult answer = InputResponses.get(request.inputResponses(),
                    CONFIRMATION_INPUT_KEY, McpSchema.ElicitResult.class, jsonMapper);
            return McpSyncResponse.result(handleConfirmationRetry(requestContext, descriptor,
                    control, invocationInput, context, challengeId, answer));
        }
        try {
            CapabilityToolCall call = buildCall(descriptor, invocationInput, context,
                    firstPresent(invocationInput.getIdempotencyKey(), control.idempotencyKey()),
                    firstPresent(invocationInput.getConfirmationToken(), control.confirmationToken()));
            return McpSyncResponse.result(invokeWithTenantContext(requestContext, descriptor, call,
                    context.getTenantId()));
        } catch (IllegalArgumentException exception) {
            return McpSyncResponse.result(errorResult(CapabilityStatus.FAILURE, "BAD_REQUEST",
                    exception.getMessage(), false, null, null));
        } catch (RuntimeException exception) {
            LOGGER.warn("Unexpected MCP tool invocation failure: capability={}, exceptionType={}",
                    descriptor.getCapabilityName(), exception.getClass().getName());
            return McpSyncResponse.result(errorResult(CapabilityStatus.FAILURE, INTERNAL_ERROR_CODE,
                    INTERNAL_ERROR_MESSAGE, false, null, null));
        }
    }

    /**
     * MRTR 重试：按签名 state 里的 challengeId 完成确认，再正常执行。
     *
     * <ul>
     * <li>无答案（只有 state、没有 inputResponses）：不新建 challenge、不执行，
     * 用同一个 challengeId 再发一次 elicitation。</li>
     * <li>decline / cancel：直接拒绝，不调 confirm、不执行。</li>
     * <li>accept：调确认服务把接受换成 token，再带 token 走正常执行；challenge
     * 校验（不存在、已过期、非 pending、不属于当前调用方）失败时返回明确的确认
     * 错误，不执行。</li>
     * </ul>
     */
    private CallToolOutcome handleConfirmationRetry(McpRequestContext requestContext,
                                                    CapabilityToolDescriptor descriptor,
                                                    AcfMcpToolProtocolMetadata.ToolCallControl control,
                                                    CapabilityToolInvocationInput invocationInput,
                                                    CapabilityContext context,
                                                    String challengeId,
                                                    McpSchema.ElicitResult answer) {
        if (answer == null) {
            if (confirmationService == null) {
                return errorResult(CapabilityStatus.FAILURE,
                        AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE,
                        "Capability requires confirmation before execution, "
                                + "but no confirmation service is configured.",
                        false, context.getTraceId(), null);
            }
            if (!supportsElicitationForm(requestContext)) {
                return errorResult(CapabilityStatus.FAILURE,
                        AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE,
                        "Capability requires confirmation, but the client did not declare "
                                + "elicitation (form) support.",
                        false, context.getTraceId(), null);
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(AcfMcpToolProtocolMetadata.STATUS, CapabilityStatus.CONFIRM_REQUIRED.name());
            AcfMcpToolProtocolMetadata.putIfPresent(metadata, AcfMcpToolProtocolMetadata.TRACE_ID,
                    context.getTraceId());
            metadata.put(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE,
                    confirmationChallengeSummary(challengeId, descriptor));
            return McpSchema.InputRequiredResult.builder()
                    .elicit(CONFIRMATION_INPUT_KEY, McpSchema.ElicitFormRequest.builder(
                            "Capability '" + descriptor.getCapabilityName()
                                    + "' still requires your confirmation before it runs. "
                                    + "Accept only if you have checked the call arguments.",
                            confirmationFormSchema()).build())
                    .requestState(challengeId)
                    .meta(metadata)
                    .build();
        }
        if (answer.action() != McpSchema.ElicitResult.Action.ACCEPT) {
            // 拒绝要落盘：把 challenge 置为 DECLINED 终态，之后再用同一 challengeId
            // 调 confirm() 会因“非 pending”被拒绝；拒绝只是建议性的 bug 在此修复。
            // decline() 本身尽力而为（未接线 / 已终态 / 不属于当前调用方时静默返回），
            // 这里的失败不改变 DENIED 回答，只记一条警告日志。
            if (confirmationService != null) {
                try {
                    declineWithTenantContext(context, challengeId);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Failed to record confirmation decline: capability={}, exceptionType={}",
                            descriptor.getCapabilityName(), exception.getClass().getName());
                }
            }
            String declined = answer.action() == McpSchema.ElicitResult.Action.DECLINE
                    ? "Capability confirmation declined by the user."
                    : "Capability confirmation cancelled by the user.";
            return errorResult(CapabilityStatus.DENIED, AcfCapabilityErrorCodes.CONFIRMATION_DECLINED,
                    declined, false, context.getTraceId(), null);
        }
        if (confirmationService == null) {
            return errorResult(CapabilityStatus.FAILURE,
                    AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE,
                    "Capability confirmation is not configured", false, null, null);
        }
        String confirmRemark;
        try {
            confirmRemark = extractConfirmRemark(answer);
        } catch (IllegalArgumentException exception) {
            return errorResult(CapabilityStatus.FAILURE, "BAD_REQUEST",
                    exception.getMessage(), false, null, null);
        }
        CapabilityConfirmationToken token;
        try {
            token = confirmWithTenantContext(context, challengeId, confirmRemark);
        } catch (IllegalArgumentException exception) {
            return errorResult(CapabilityStatus.FAILURE,
                    AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID, exception.getMessage(), false, null, null);
        } catch (RuntimeException exception) {
            LOGGER.warn("MCP confirmation failed: capability={}, exceptionType={}",
                    descriptor.getCapabilityName(), exception.getClass().getName());
            return errorResult(CapabilityStatus.FAILURE,
                    AcfCapabilityErrorCodes.CONFIRMATION_ERROR,
                    "Capability confirmation failed", false, null, null);
        }
        // confirm() 按约定总返回带 token 的结果；防御性地处理返回 null 或 token
        // 为空的实现，不让 NullPointerException 漏到兜底分支变成含糊的内部错误。
        if (token == null || token.getConfirmationToken() == null
                || token.getConfirmationToken().isBlank()) {
            LOGGER.warn("MCP confirmation returned no token: capability={}", descriptor.getCapabilityName());
            return errorResult(CapabilityStatus.FAILURE,
                    AcfCapabilityErrorCodes.CONFIRMATION_ERROR,
                    "Capability confirmation failed", false, null, null);
        }
        try {
            CapabilityToolCall call = buildCall(descriptor, invocationInput, context,
                    firstPresent(invocationInput.getIdempotencyKey(), control.idempotencyKey()),
                    token.getConfirmationToken());
            return invokeWithTenantContext(requestContext, descriptor, call, context.getTenantId());
        } catch (IllegalArgumentException exception) {
            return errorResult(CapabilityStatus.FAILURE, "BAD_REQUEST",
                    exception.getMessage(), false, null, null);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unexpected MCP tool invocation failure: capability={}, exceptionType={}",
                    descriptor.getCapabilityName(), exception.getClass().getName());
            return errorResult(CapabilityStatus.FAILURE, INTERNAL_ERROR_CODE,
                    INTERNAL_ERROR_MESSAGE, false, null, null);
        }
    }

    private CapabilityConfirmationToken confirmWithTenantContext(CapabilityContext context,
                                                                 String challengeId, String confirmRemark) {
        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            setTenantId(context.getTenantId());
            return confirmationService.confirm(challengeId, context, confirmRemark);
        } finally {
            setTenantId(previousTenantId);
        }
    }

    private void declineWithTenantContext(CapabilityContext context, String challengeId) {
        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            setTenantId(context.getTenantId());
            confirmationService.decline(challengeId, context);
        } finally {
            setTenantId(previousTenantId);
        }
    }

    /**
     * 重试时手头只有 challengeId（完整 challenge 对象只在首轮才有），_meta 里
     * 的 challenge 摘要只放能确定的两项：challengeId 与能力名。首轮的完整摘要
     * 见 {@link #confirmationChallenge(CapabilityConfirmationChallenge)}。
     */
    private static Map<String, Object> confirmationChallengeSummary(String challengeId,
                                                                     CapabilityToolDescriptor descriptor) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "challengeId", challengeId);
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, "capabilityName",
                descriptor == null ? null : descriptor.getCapabilityName());
        return metadata;
    }

    private static String extractConfirmRemark(McpSchema.ElicitResult answer) {
        Map<String, Object> content = answer.content();
        if (content == null) {
            return null;
        }
        Object remark = content.get(CONFIRM_REMARK_FIELD);
        if (remark == null) {
            return null;
        }
        if (!(remark instanceof String text)) {
            throw new IllegalArgumentException("confirmRemark must be a string");
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private CapabilityToolCall buildCall(CapabilityToolDescriptor descriptor,
                                         CapabilityToolInvocationInput invocationInput,
                                         CapabilityContext context, String idempotencyKey,
                                         String confirmationToken) {
        return CapabilityToolCall.builder()
                .capabilityName(descriptor.getCapabilityName())
                .arguments(adaptArguments(descriptor, invocationInput.getBusinessArguments()))
                .context(context)
                .idempotencyKey(idempotencyKey)
                .confirmationToken(confirmationToken)
                .build();
    }

    private CallToolOutcome invokeWithTenantContext(McpRequestContext requestContext,
                                                    CapabilityToolDescriptor descriptor,
                                                    CapabilityToolCall call, Long tenantId) {
        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            setTenantId(tenantId);
            CapabilityResult result = capabilityToolInvoker.invoke(call);
            return adaptOutcome(requestContext, descriptor, result);
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

    private static String firstPresent(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Object adaptArguments(CapabilityToolDescriptor descriptor, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (isObjectSchema(descriptor.getInputSchema())) {
            return safeArguments;
        }
        return safeArguments.get(McpSchemaAdapter.INPUT_VALUE_PROPERTY);
    }

    private CallToolOutcome adaptOutcome(McpRequestContext requestContext,
                                         CapabilityToolDescriptor descriptor,
                                         CapabilityResult result) {
        if (result == null) {
            return errorResult(CapabilityStatus.FAILURE, null, "Capability invocation failed",
                    false, null, null);
        }
        if (result.getStatus() == CapabilityStatus.SUCCESS) {
            return adaptSuccessResult(descriptor, result);
        }
        CapabilityConfirmationChallenge challenge = result.getData() instanceof CapabilityConfirmationChallenge item
                ? item : null;
        if (result.getStatus() == CapabilityStatus.CONFIRM_REQUIRED && challenge != null) {
            return adaptConfirmationRequired(requestContext, result, challenge);
        }
        return errorResult(result.getStatus(), result.getErrorCode(),
                defaultMessage(result.getMessage(), "Capability invocation failed"),
                result.isRetryable(), result.getTraceId(), challenge);
    }

    /**
     * 需要确认：不再返回带 challenge 的错误结果让客户端去调独立确认工具，
     * 而是直接返回 InputRequiredResult 做表单 elicitation。
     *
     * <p>两种降级都不静默执行：确认服务没接线时回旧式错误结果（challenge 仍放
     * _meta 供排查，但文案不再指引任何确认工具）；客户端没声明 elicitation
     * 表单能力时回明确的 CONFIRMATION_UNAVAILABLE——此时去 elicite 的话 SDK
     * 会把未声明的 input 请求当作服务端 bug 以内部错误回答。</p>
     */
    private CallToolOutcome adaptConfirmationRequired(McpRequestContext requestContext,
                                                      CapabilityResult result,
                                                      CapabilityConfirmationChallenge challenge) {
        if (confirmationService == null) {
            return errorResult(result.getStatus(), result.getErrorCode(),
                    "Capability requires confirmation before execution, "
                            + "but no confirmation service is configured.",
                    result.isRetryable(), result.getTraceId(), challenge);
        }
        if (!supportsElicitationForm(requestContext)) {
            return errorResult(CapabilityStatus.FAILURE,
                    AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE,
                    "Capability requires confirmation, but the client did not declare "
                            + "elicitation (form) support.",
                    false, result.getTraceId(), challenge);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(AcfMcpToolProtocolMetadata.STATUS, CapabilityStatus.CONFIRM_REQUIRED.name());
        AcfMcpToolProtocolMetadata.putIfPresent(metadata, AcfMcpToolProtocolMetadata.TRACE_ID,
                result.getTraceId());
        metadata.put(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE, confirmationChallenge(challenge));
        return McpSchema.InputRequiredResult.builder()
                .elicit(CONFIRMATION_INPUT_KEY, McpSchema.ElicitFormRequest.builder(
                        confirmationMessage(challenge), confirmationFormSchema()).build())
                .requestState(challenge.getChallengeId())
                .meta(metadata)
                .build();
    }

    private static boolean supportsElicitationForm(McpRequestContext requestContext) {
        return requestContext != null && requestContext.clientCapabilities() != null
                && requestContext.clientCapabilities().supportsElicitationForm();
    }

    private static String confirmationMessage(CapabilityConfirmationChallenge challenge) {
        StringBuilder message = new StringBuilder("Capability '")
                .append(challenge.getCapabilityName())
                .append("' requires your confirmation before it runs.");
        if (challenge.getRiskLevel() != null) {
            message.append(" Risk level: ").append(challenge.getRiskLevel().name()).append(".");
        }
        if (challenge.getExpiresAt() != null) {
            message.append(" Confirmation expires at ").append(challenge.getExpiresAt()).append(".");
        }
        message.append(" Accept only if you have checked the call arguments.");
        return message.toString();
    }

    /**
     * 确认表单：只有可选的确认备注一个顶层字段。elicitation 的 requestedSchema
     * 只允许顶层属性、不允许嵌套，本表单天然满足；不设 required（全部可选）。
     */
    private static Map<String, Object> confirmationFormSchema() {
        Map<String, Object> remark = new LinkedHashMap<>();
        remark.put("type", "string");
        remark.put("description", "Optional note recorded with the confirmation (max "
                + CONFIRM_REMARK_MAX_LENGTH + " characters).");
        remark.put("maxLength", CONFIRM_REMARK_MAX_LENGTH);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(CONFIRM_REMARK_FIELD, remark));
        schema.put("additionalProperties", false);
        return schema;
    }

    private McpSchema.CallToolResult adaptSuccessResult(CapabilityToolDescriptor descriptor,
                                                        CapabilityResult result) {
        Object structuredContent = structuredContentNormalizer.normalize(
                adaptStructuredContent(descriptor, result.getData()));
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .addContent(TextContent.builder(serializeStructuredContent(structuredContent)).build())
                .structuredContent(structuredContent)
                .meta(resultMetadata(result))
                .isError(false);
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            builder.addContent(TextContent.builder(result.getMessage()).build());
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
        // Modern TextContent.builder(text) 要求 text 非空，异常 message 可能为 null 时给默认值。
        String text = message == null ? "Capability invocation failed" : message;
        return McpSchema.CallToolResult.builder()
                .addContent(TextContent.builder(text).build())
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
