package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
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
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("demo.echo",
                Map.of("message", "hello",
                        CapabilityToolContractSupport.IDEMPOTENCY_KEY, "idem-001",
                        CapabilityToolContractSupport.CONFIRMATION_TOKEN, "confirm-001",
                        CapabilityToolContractSupport.CLIENT_REQUEST_ID, "request-001"),
                Map.of());

        McpTransportContext transportContext = McpTransportContext.create(Map.of(
                McpTransportContextKeys.USER_ID, 1L,
                McpTransportContextKeys.TENANT_ID, 2L,
                McpTransportContextKeys.CONSUMER_ID, "user:1"));
        TenantContextHolder.setTenantId(9L);
        McpSchema.CallToolResult result;
        try {
            result = createHandler(invoker).handle(transportContext, descriptor, request);
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
    void shouldFallbackToMetadataControlFieldsForCompatibleClients() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture()))
                .thenReturn(CapabilityResult.success("demo.echo", Map.of("message", "hello")));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("demo.echo",
                Map.of("message", "hello"), Map.of(
                AcfMcpToolProtocolMetadata.IDEMPOTENCY_KEY, "idem-meta",
                AcfMcpToolProtocolMetadata.CONFIRMATION_TOKEN, "confirm-meta",
                AcfMcpToolProtocolMetadata.CLIENT_REQUEST_ID, "request-meta"));

        McpSchema.CallToolResult result = createHandler(invoker)
                .handle(McpTransportContext.EMPTY, descriptor, request);

        assertThat(result.isError()).isFalse();
        CapabilityToolCall call = callCaptor.getValue();
        assertThat(call.getArguments()).isEqualTo(Map.of("message", "hello"));
        assertThat(call.getContext().getClientRequestId()).isEqualTo("request-meta");
        assertThat(call.getIdempotencyKey()).isEqualTo("idem-meta");
        assertThat(call.getConfirmationToken()).isEqualTo("confirm-meta");
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

        McpSchema.CallToolResult result = createHandler(invoker)
                .handle(McpTransportContext.EMPTY, descriptor, request);

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

        McpSchema.CallToolResult result = createHandler(invoker).handle(McpTransportContext.EMPTY, descriptor,
                McpSchema.CallToolRequest.builder("demo.order").arguments(Map.of()).build());

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

        McpSchema.CallToolResult result = createHandler(invoker).handle(McpTransportContext.EMPTY, descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

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

        McpSchema.CallToolResult result = createHandler(invoker).handle(McpTransportContext.EMPTY, descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("Capability invocation failed");
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("jdbc:mysql://internal/db token=secret-value"));

        McpSchema.CallToolResult result = createHandler(invoker).handle(McpTransportContext.EMPTY,
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
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("demo.echo", Map.of(),
                Map.of(AcfMcpToolProtocolMetadata.IDEMPOTENCY_KEY, 123));

        McpSchema.CallToolResult result = createHandler(invoker)
                .handle(McpTransportContext.EMPTY, descriptor, request);

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE, "BAD_REQUEST");
        verifyNoInteractions(invoker);
    }

    @Test
    void shouldExposeConfirmationChallengeAsSafeMetadata() {
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

        McpSchema.CallToolResult result = createHandler(invoker).handle(McpTransportContext.EMPTY,
                descriptor, McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("acf.confirm")
                .contains("challenge-001")
                .doesNotContain("internal-digest");
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.STATUS, "CONFIRM_REQUIRED")
                .containsEntry(AcfMcpToolProtocolMetadata.TRACE_ID, "trace-confirm");
        Map<?, ?> challengeMetadata = (Map<?, ?>) result.meta()
                .get(AcfMcpToolProtocolMetadata.CONFIRMATION_CHALLENGE);
        assertThat(challengeMetadata.get("challengeId")).isEqualTo("challenge-001");
        assertThat(challengeMetadata.containsKey("requestDigest")).isFalse();
    }

    private static CapabilityToolDescriptor descriptor(Map<String, Object> inputSchema,
                                                       Map<String, Object> outputSchema) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn("demo.echo");
        when(descriptor.getInputSchema()).thenReturn(inputSchema);
        when(descriptor.getOutputSchema()).thenReturn(outputSchema);
        return descriptor;
    }

    private static AcfMcpToolCallHandler createHandler(CapabilityToolInvoker invoker) {
        return new AcfMcpToolCallHandler(invoker, new JacksonMcpJsonMapper(new ObjectMapper()),
                new AcfMcpStructuredContentNormalizer());
    }

    record TimeOutput(LocalDateTime orderTime) {
    }

}
