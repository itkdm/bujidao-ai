package cn.iocoder.yudao.module.mcp.framework.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.module.mcp.framework.security.McpTransportContextKeys;
import io.modelcontextprotocol.common.McpTransportContext;
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
        when(invoker.invoke(callCaptor.capture()))
                .thenReturn(CapabilityResult.success("demo.echo", Map.of("message", "hello")));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("demo.echo",
                Map.of("message", "hello"), Map.of(
                McpToolProtocolMetadata.IDEMPOTENCY_KEY, "idem-001",
                McpToolProtocolMetadata.CONFIRMATION_TOKEN, "confirm-001",
                McpToolProtocolMetadata.CLIENT_REQUEST_ID, "request-001"));

        McpTransportContext transportContext = McpTransportContext.create(Map.of(
                McpTransportContextKeys.USER_ID, 1L,
                McpTransportContextKeys.TENANT_ID, 2L,
                McpTransportContextKeys.CONSUMER_ID, "user:1"));
        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker)
                .handle(transportContext, descriptor, request);

        assertThat(result.isError()).isFalse();
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
    void shouldUnwrapScalarInputAndWrapScalarOutput() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "string"), Map.of("type", "integer"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture())).thenReturn(CapabilityResult.success("demo.length", 5));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.length")
                .arguments(Map.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY, "hello"))
                .build();

        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker)
                .handle(McpTransportContext.EMPTY, descriptor, request);

        assertThat(callCaptor.getValue().getArguments()).isEqualTo("hello");
        assertThat(result.structuredContent()).isEqualTo(Map.of(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY, 5));
    }

    @Test
    void shouldReturnAcfPublicFailureAsMcpError() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        when(invoker.invoke(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CapabilityResult.denied("demo.echo", "PERMISSION_DENIED", "Permission denied")
                        .withTraceId("trace-denied"));

        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker).handle(McpTransportContext.EMPTY, descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("Permission denied");
        assertThat(result.structuredContent()).isNull();
        assertThat(result.meta()).containsEntry(McpToolProtocolMetadata.STATUS, "DENIED")
                .containsEntry(McpToolProtocolMetadata.ERROR_CODE, "PERMISSION_DENIED")
                .containsEntry(McpToolProtocolMetadata.TRACE_ID, "trace-denied");
    }

    @Test
    void shouldFailClosedWhenInvokerReturnsNull() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "null"));

        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker).handle(McpTransportContext.EMPTY, descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("Capability invocation failed");
    }

    @Test
    void shouldRejectInvalidControlMetadataBeforeAcfInvocation() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("demo.echo", Map.of(),
                Map.of(McpToolProtocolMetadata.IDEMPOTENCY_KEY, 123));

        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker)
                .handle(McpTransportContext.EMPTY, descriptor, request);

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(McpToolProtocolMetadata.ERROR_CODE, "BAD_REQUEST");
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

        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker).handle(McpTransportContext.EMPTY,
                descriptor, McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(McpToolProtocolMetadata.STATUS, "CONFIRM_REQUIRED")
                .containsEntry(McpToolProtocolMetadata.TRACE_ID, "trace-confirm");
        Map<?, ?> challengeMetadata = (Map<?, ?>) result.meta()
                .get(McpToolProtocolMetadata.CONFIRMATION_CHALLENGE);
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

}
