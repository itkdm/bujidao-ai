package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcfMcpToolSpecificationProviderTest {

    private final CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
    private final AcfMcpToolMapper toolMapper = new AcfMcpToolMapper();
    private final AcfMcpToolCallHandler toolCallHandler = mock(AcfMcpToolCallHandler.class);

    @Test
    void shouldExposeNothingWhenNoCapabilityDeclared() {
        when(catalog.listDeclared()).thenReturn(List.of());

        assertThat(createFactory().createToolSpecifications()).isEmpty();
    }

    @Test
    void shouldMapAllDeclaredCapabilitiesInRegistryOrder() {
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        when(catalog.listDeclared()).thenReturn(List.of(first, second));

        List<McpStatelessServerFeatures.SyncToolSpecification> specifications =
                createFactory().createToolSpecifications();

        assertThat(specifications).extracting(specification -> specification.tool().name())
                .containsExactly("demo.first", "demo.second");
        McpSchema.Tool tool = specifications.get(0).tool();
        assertThat(tool.title()).isEqualTo("Demo Tool");
        assertThat(tool.inputSchema()).containsEntry("type", "object");
        assertThat(tool.annotations().readOnlyHint()).isTrue();
        assertThat(tool.annotations().idempotentHint()).isTrue();
        assertThat(tool.annotations().destructiveHint()).isNull();
        assertThat(tool.annotations().openWorldHint()).isNull();
        assertThat(tool.meta()).containsEntry(AcfMcpToolProtocolMetadata.CAPABILITY_VERSION, "1.0.0")
                .containsEntry(AcfMcpToolProtocolMetadata.RISK_LEVEL, "LOW")
                .containsEntry(AcfMcpToolProtocolMetadata.IDEMPOTENCY_REQUIRED, false)
                .containsEntry(AcfMcpToolProtocolMetadata.CONFIRMATION_REQUIRED, false);
    }

    @Test
    void shouldRegisterSideEffectCapabilitiesDeclaredByCode() {
        CapabilityToolDescriptor descriptor = descriptor("demo.write", true, true);
        when(catalog.listDeclared()).thenReturn(List.of(descriptor));

        McpSchema.Tool tool = createFactory().createToolSpecifications().get(0).tool();
        assertThat(tool.annotations().readOnlyHint()).isFalse();
        Map<?, ?> properties = (Map<?, ?>) tool.inputSchema().get("properties");
        assertThat(properties.keySet().stream().map(String::valueOf).toList())
                .contains(CapabilityToolContractSupport.CLIENT_REQUEST_ID,
                        CapabilityToolContractSupport.IDEMPOTENCY_KEY,
                        CapabilityToolContractSupport.CONFIRMATION_TOKEN);
        assertThat(((List<?>) tool.inputSchema().get("required")).stream().map(String::valueOf).toList())
                .contains(CapabilityToolContractSupport.IDEMPOTENCY_KEY);
        assertThat(tool.meta()).containsEntry(AcfMcpToolProtocolMetadata.IDEMPOTENCY_REQUIRED, true)
                .containsEntry(AcfMcpToolProtocolMetadata.CONFIRMATION_REQUIRED, true);
    }

    private AcfMcpToolSpecificationProvider createFactory() {
        return new AcfMcpToolSpecificationProvider(catalog, toolMapper, toolCallHandler);
    }

    private static CapabilityToolDescriptor descriptor(String name, boolean sideEffect,
                                                       boolean confirmationRequired) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn(name);
        when(descriptor.getTitle()).thenReturn("Demo Tool");
        when(descriptor.getDescription()).thenReturn("Demo description");
        when(descriptor.getVersion()).thenReturn("1.0.0");
        when(descriptor.getRiskLevel()).thenReturn(CapabilityRiskLevel.LOW);
        when(descriptor.getInputSchema()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(descriptor.getOutputSchema()).thenReturn(Map.of("type", "string"));
        when(descriptor.isSideEffect()).thenReturn(sideEffect);
        when(descriptor.isConfirmationRequired()).thenReturn(confirmationRequired);
        when(descriptor.isIdempotencyRequired()).thenReturn(sideEffect || confirmationRequired);
        return descriptor;
    }

}
