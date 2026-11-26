package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.mcp.tool.McpSchemaAdapter;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AcfMcpToolCallHandler} 单测：modern 签名
 * {@code handle(McpRequestContext, CapabilityToolDescriptor, McpSchema.CallToolRequest)}
 * 返回 {@code McpSyncResponse<CallToolOutcome>}。
 *
 * <p>控制字段（idempotencyKey / confirmationToken / clientRequestId）只从
 * {@code requestContext.meta()} 读：业务参数里的同名字段仍由
 * {@link CapabilityToolContractSupport} 剥离，但请求 {@code _meta}（即
 * {@link McpSchema.CallToolRequest#meta()}）里的控制字段不再回退读取。</p>
 */
class AcfMcpToolCallHandlerTest {

    @Test
    void shouldInvokeObjectCapabilityThroughAcfBoundary() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture())).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(2L);
            return CapabilityResult.success("demo.echo", Map.of("message", "hello"));
        });
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello",
                        CapabilityToolContractSupport.IDEMPOTENCY_KEY, "idem-001",
                        CapabilityToolContractSupport.CONFIRMATION_TOKEN, "confirm-001",
                        CapabilityToolContractSupport.CLIENT_REQUEST_ID, "request-001"))
                .build();

        McpRequestContext requestContext = requestContext(Map.of());
        TenantContextHolder.setTenantId(9L);
        McpSchema.CallToolResult result;
        try {
            result = callToolResult(createHandler(invoker), requestContext, descriptor, request);
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(9L);
        } finally {
            TenantContextHolder.clear();
        }

        assertThat(result.isError()).isFalse();
        assertThat(result.content().get(0).toString()).contains("{\"message\":\"hello\"}");
        assertThat(result.content()).hasSize(1);
        assertThat(result.structuredContent()).isEqualTo(Map.of("message", "hello"));
        CapabilityToolCall call = callCaptor.getValue();
        assertThat(call.getCapabilityName()).isEqualTo("demo.echo");
        assertThat(call.getArguments()).isEqualTo(Map.of("message", "hello"));
        assertThat(call.getContext().getSource()).isEqualTo("MCP");
        assertThat(call.getContext().getConsumerType()).isEqualTo(CapabilityConsumerType.MCP);
        assertThat(call.getContext().getUserId()).isEqualTo(1L);
        assertThat(call.getContext().getTenantId()).isEqualTo(2L);
        assertThat(call.getContext().getConsumerId()).isEqualTo("user:1");
        assertThat(call.getContext().getClientRequestId()).isEqualTo("request-001");
        assertThat(call.getIdempotencyKey()).isEqualTo("idem-001");
        assertThat(call.getConfirmationToken()).isEqualTo("confirm-001");
        verify(invoker).invoke(call);
    }

    @Test
    void shouldReadControlFieldsFromRequestContextMeta() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture()))
                .thenReturn(CapabilityResult.success("demo.echo", Map.of("message", "hello")));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .build();

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker),
                requestContext(Map.of(
                        AcfMcpToolProtocolMetadata.IDEMPOTENCY_KEY, "idem-meta",
                        AcfMcpToolProtocolMetadata.CONFIRMATION_TOKEN, "confirm-meta",
                        AcfMcpToolProtocolMetadata.CLIENT_REQUEST_ID, "request-meta")),
                descriptor, request);

        assertThat(result.isError()).isFalse();
        CapabilityToolCall call = callCaptor.getValue();
        assertThat(call.getArguments()).isEqualTo(Map.of("message", "hello"));
        assertThat(call.getContext().getClientRequestId()).isEqualTo("request-meta");
        assertThat(call.getIdempotencyKey()).isEqualTo("idem-meta");
        assertThat(call.getConfirmationToken()).isEqualTo("confirm-meta");
    }

    @Test
    void shouldIgnoreControlFieldsInRequestMeta() {
        // 反向测试：控制字段只放请求 _meta（CallToolRequest.meta），不放
        // requestContext.meta() 时必须被忽略——handler 不再从 request.meta() 回退。
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture()))
                .thenReturn(CapabilityResult.success("demo.echo", Map.of("message", "hello")));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .meta(Map.of(
                        AcfMcpToolProtocolMetadata.IDEMPOTENCY_KEY, "idem-request-meta",
                        AcfMcpToolProtocolMetadata.CONFIRMATION_TOKEN, "confirm-request-meta",
                        AcfMcpToolProtocolMetadata.CLIENT_REQUEST_ID, "request-request-meta"))
                .build();

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker),
                requestContext(Map.of()), descriptor, request);

        assertThat(result.isError()).isFalse();
        CapabilityToolCall call = callCaptor.getValue();
        assertThat(call.getArguments()).isEqualTo(Map.of("message", "hello"));
        assertThat(call.getContext().getClientRequestId()).isNull();
        assertThat(call.getIdempotencyKey()).isNull();
        assertThat(call.getConfirmationToken()).isNull();
    }

    @Test
    void shouldUnwrapScalarInputAndWrapScalarOutput() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "string"), Map.of("type", "integer"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture()))
                .thenReturn(CapabilityResult.success("demo.length", 5, "Length calculated"));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.length")
                .arguments(Map.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY, "hello",
                        CapabilityToolContractSupport.IDEMPOTENCY_KEY, "idem-scalar"))
                .build();

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker),
                requestContext(Map.of()), descriptor, request);

        assertThat(callCaptor.getValue().getArguments()).isEqualTo("hello");
        assertThat(callCaptor.getValue().getIdempotencyKey()).isEqualTo("idem-scalar");
        assertThat(result.content().get(0).toString()).contains("{\"result\":5}");
        assertThat(result.content().get(1).toString()).contains("Length calculated");
        assertThat(result.structuredContent()).isEqualTo(Map.of(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY, 5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeStructuredContentToJsonNativeValues() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of(
                "type", "object",
                "properties", Map.of("orderTime", Map.of("type", "string", "format", "date-time"))));
        LocalDateTime orderTime = LocalDateTime.of(2026, 7, 30, 9, 15, 30);
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CapabilityResult.success("demo.order", new TimeOutput(orderTime), "Order returned"));

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker), requestContext(Map.of()),
                descriptor, McpSchema.CallToolRequest.builder("demo.order").arguments(Map.of()).build());

        assertThat(result.isError()).isFalse();
        Map<String, Object> structuredContent = (Map<String, Object>) result.structuredContent();
        assertThat(structuredContent).containsEntry("orderTime", "2026-07-30T09:15:30");
        assertThat(result.content().get(0).toString()).contains("\"orderTime\":\"2026-07-30T09:15:30\"");
    }

    @Test
    void shouldReturnAcfPublicFailureAsMcpError() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CapabilityResult.denied("demo.echo", "PERMISSION_DENIED", "Permission denied")
                        .withTraceId("trace-denied"));

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker), requestContext(Map.of()),
                descriptor, McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("Permission denied");
        assertThat(result.structuredContent()).isNull();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "DENIED")
                .containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE, "PERMISSION_DENIED")
                .containsEntry(AcfMcpToolProtocolMetadata.TRACE_ID, "trace-denied");
    }

    @Test
    void shouldFailClosedWhenInvokerReturnsNull() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "null"));

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker), requestContext(Map.of()),
                descriptor, McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("Capability invocation failed");
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("jdbc:mysql://internal/db token=secret-value"));

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker), requestContext(Map.of()),
                descriptor, McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE, "INTERNAL_ERROR");
        assertThat(result.content().get(0).toString())
                .contains("Capability invocation failed")
                .doesNotContain("jdbc:mysql", "internal", "secret-value");
    }

    @Test
    void shouldRejectInvalidControlMetadataBeforeAcfInvocation() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of())
                .build();

        McpSchema.CallToolResult result = callToolResult(createHandler(invoker),
                requestContext(Map.of(AcfMcpToolProtocolMetadata.IDEMPOTENCY_KEY, 123)),
                descriptor, request);

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE, "BAD_REQUEST");
        verifyNoInteractions(invoker);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnInputRequiredResultForConfirmationChallenge() {
        // 旧行为（带 challenge 的错误结果 + 客户端调 acf.confirm）已废弃：
        // 首轮 CONFIRM_REQUIRED 现在返回 InputRequiredResult 做表单 elicitation，
        // _meta 里的 challenge 摘要只含安全字段，不含 requestDigest / token。
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        CapabilityConfirmationChallenge challenge = CapabilityConfirmationChallenge.builder()
                .challengeId("challenge-001")
                .capabilityName("demo.echo")
                .capabilityVersion("1.0.0")
                .riskLevel(CapabilityRiskLevel.HIGH)
                .expiresAt(LocalDateTime.of(2026, 7, 24, 20, 0))
                .requestDigest("internal-digest")
                .build();
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CapabilityResult.confirmationRequired("demo.echo", challenge)
                        .withTraceId("trace-confirm"));

        McpSchema.CallToolOutcome outcome = handle(createHandler(invoker,
                        mock(CapabilityConfirmationService.class)),
                formRequestContext(), descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(outcome).isInstanceOf(McpSchema.InputRequiredResult.class);
        McpSchema.InputRequiredResult required = (McpSchema.InputRequiredResult) outcome;
        assertThat(required.resultType()).isEqualTo(McpSchema.ResultType.INPUT_REQUIRED);
        assertThat(required.requestState()).isEqualTo("challenge-001");
        assertThat(required.inputRequests()).containsOnlyKeys(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY);
        assertThat(required.inputRequests().get(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY))
                .isInstanceOf(McpSchema.ElicitInputRequest.class);
        McpSchema.ElicitFormRequest form = (McpSchema.ElicitFormRequest)
                ((McpSchema.ElicitInputRequest) required.inputRequests()
                        .get(AcfMcpToolCallHandler.CONFIRMATION_INPUT_KEY)).params();
        assertThat(form.message()).contains("demo.echo");
        Map<String, Object> schema = form.requestedSchema();
        assertThat(schema).containsEntry("type", "object")
                .containsEntry("additionalProperties", false)
                .doesNotContainKey("required");
        assertThat((Map<String, Object>) schema.get("properties")).containsOnlyKeys(
                AcfMcpToolCallHandler.CONFIRM_REMARK_FIELD);
        Map<String, Object> remark = (Map<String, Object>) ((Map<String, Object>) schema.get("properties"))
                .get(AcfMcpToolCallHandler.CONFIRM_REMARK_FIELD);
        assertThat(remark).containsEntry("type", "string").containsEntry("maxLength", 512);
        assertThat(required.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "CONFIRM_REQUIRED")
                .containsEntry(AcfMcpToolProtocolMetadata.TRACE_ID, "trace-confirm");
        Map<String, Object> challengeMetadata = (Map<String, Object>) required.meta()
                .get(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE);
        assertThat(challengeMetadata.keySet()).containsExactlyInAnyOrder(
                "challengeId", "capabilityName", "capabilityVersion", "riskLevel", "expiresAt");
        assertThat(challengeMetadata.get("challengeId")).isEqualTo("challenge-001");
        assertThat(challengeMetadata.get("capabilityVersion")).isEqualTo("1.0.0");
        assertThat(challengeMetadata.get("riskLevel")).isEqualTo("HIGH");
        assertThat(challengeMetadata.get("expiresAt")).isEqualTo("2026-07-24T20:00");
        assertThat(challengeMetadata.containsKey("requestDigest")).isFalse();
        assertThat(challengeMetadata.containsKey("confirmationToken")).isFalse();
        assertThat(required.meta().toString()).doesNotContain("internal-digest");
    }

    static CapabilityToolDescriptor descriptor(Map<String, Object> inputSchema,
                                               Map<String, Object> outputSchema) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn("demo.echo");
        when(descriptor.getInputSchema()).thenReturn(inputSchema);
        when(descriptor.getOutputSchema()).thenReturn(outputSchema);
        return descriptor;
    }

    static McpRequestContext requestContext(Map<String, Object> meta) {
        McpRequestContext requestContext = mock(McpRequestContext.class);
        when(requestContext.transportContext()).thenReturn(McpTransportContext.create(Map.of(
                McpTransportContextKeys.USER_ID, 1L,
                McpTransportContextKeys.TENANT_ID, 2L,
                McpTransportContextKeys.CONSUMER_ID, "user:1")));
        when(requestContext.meta()).thenReturn(meta);
        when(requestContext.clientCapabilities()).thenReturn(McpSchema.ClientCapabilities.NONE);
        return requestContext;
    }

    static McpRequestContext formRequestContext() {
        McpRequestContext requestContext = requestContext(Map.of());
        when(requestContext.clientCapabilities()).thenReturn(McpSchema.ClientCapabilities.builder()
                .elicitation(new McpSchema.ClientCapabilities.Elicitation(
                        new McpSchema.ClientCapabilities.Elicitation.Form(), null))
                .build());
        return requestContext;
    }

    static McpSchema.CallToolOutcome handle(AcfMcpToolCallHandler handler, McpRequestContext requestContext,
                                            CapabilityToolDescriptor descriptor,
                                            McpSchema.CallToolRequest request) {
        McpSyncResponse<McpSchema.CallToolOutcome> response = handler.handle(requestContext, descriptor, request);
        assertThat(response).isInstanceOf(McpSyncResponse.Result.class);
        return ((McpSyncResponse.Result<McpSchema.CallToolOutcome>) response).result();
    }

    static McpSchema.CallToolResult callToolResult(AcfMcpToolCallHandler handler,
                                                   McpRequestContext requestContext,
                                                   CapabilityToolDescriptor descriptor,
                                                   McpSchema.CallToolRequest request) {
        McpSchema.CallToolOutcome outcome = handle(handler, requestContext, descriptor, request);
        assertThat(outcome).isInstanceOf(McpSchema.CallToolResult.class);
        return (McpSchema.CallToolResult) outcome;
    }

    static AcfMcpToolCallHandler createHandler(CapabilityToolInvoker invoker) {
        return new AcfMcpToolCallHandler(invoker, new JacksonMcpJsonMapper(new ObjectMapper()),
                new AcfMcpStructuredContentNormalizer());
    }

    static AcfMcpToolCallHandler createHandler(CapabilityToolInvoker invoker,
                                               CapabilityConfirmationService confirmationService) {
        return new AcfMcpToolCallHandler(invoker, new JacksonMcpJsonMapper(new ObjectMapper()),
                new AcfMcpStructuredContentNormalizer(),
                new CapabilityToolContractSupport(),
                confirmationService);
    }

    record TimeOutput(LocalDateTime orderTime) {
    }

}
