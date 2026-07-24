package cn.iocoder.yudao.module.mcp.framework.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcfMcpToolCallHandlerTest {

    @Test
    void shouldInvokeObjectCapabilityThroughAcfBoundary() {
        CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
        CapabilityToolDescriptor descriptor = descriptor(Map.of("type", "object"), Map.of("type", "object"));
        ArgumentCaptor<CapabilityToolCall> callCaptor = ArgumentCaptor.forClass(CapabilityToolCall.class);
        when(invoker.invoke(callCaptor.capture()))
                .thenReturn(CapabilityResult.success("demo.echo", Map.of("message", "hello")));
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("demo.echo")
                .arguments(Map.of("message", "hello"))
                .build();

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
                .thenReturn(CapabilityResult.denied("demo.echo", "PERMISSION_DENIED", "Permission denied"));

        McpSchema.CallToolResult result = new AcfMcpToolCallHandler(invoker).handle(McpTransportContext.EMPTY, descriptor,
                McpSchema.CallToolRequest.builder("demo.echo").arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.content().get(0).toString()).contains("Permission denied");
        assertThat(result.structuredContent()).isNull();
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

    private static CapabilityToolDescriptor descriptor(Map<String, Object> inputSchema,
                                                       Map<String, Object> outputSchema) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn("demo.echo");
        when(descriptor.getInputSchema()).thenReturn(inputSchema);
        when(descriptor.getOutputSchema()).thenReturn(outputSchema);
        return descriptor;
    }

}
