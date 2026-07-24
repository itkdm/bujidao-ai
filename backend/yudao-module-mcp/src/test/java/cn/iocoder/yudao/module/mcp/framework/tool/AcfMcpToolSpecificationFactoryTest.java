package cn.iocoder.yudao.module.mcp.framework.tool;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.module.mcp.framework.config.YudaoMcpToolProperties;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcfMcpToolSpecificationFactoryTest {

    private final CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
    private final YudaoMcpToolProperties properties = new YudaoMcpToolProperties();
    private final AcfMcpToolCallHandler toolCallHandler = mock(AcfMcpToolCallHandler.class);

    @Test
    void shouldExposeNothingWithoutExplicitWhitelist() {
        assertThat(createFactory().createToolSpecifications()).isEmpty();
    }

    @Test
    void shouldMapWhitelistedCapabilitiesInConfiguredOrder() {
        properties.setExposedCapabilities(List.of("demo.second", "demo.first"));
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        when(catalog.getDeclared("demo.first")).thenReturn(first);
        when(catalog.getDeclared("demo.second")).thenReturn(second);

        List<McpStatelessServerFeatures.SyncToolSpecification> specifications =
                createFactory().createToolSpecifications();

        assertThat(specifications).extracting(specification -> specification.tool().name())
                .containsExactly("demo.second", "demo.first");
        McpSchema.Tool tool = specifications.get(0).tool();
        assertThat(tool.title()).isEqualTo("Demo Tool");
        assertThat(tool.inputSchema()).containsEntry("type", "object");
        assertThat(tool.annotations().readOnlyHint()).isTrue();
        assertThat(tool.annotations().openWorldHint()).isFalse();
        assertThat(tool.meta()).containsEntry(McpToolProtocolMetadata.CAPABILITY_VERSION, "1.0.0")
                .containsEntry(McpToolProtocolMetadata.RISK_LEVEL, "LOW")
                .containsEntry(McpToolProtocolMetadata.IDEMPOTENCY_REQUIRED, false)
                .containsEntry(McpToolProtocolMetadata.CONFIRMATION_REQUIRED, false);
    }

    @Test
    void shouldRejectBlankAndDuplicateCapabilityNames() {
        properties.setExposedCapabilities(List.of("demo.first", " demo.first "));
        CapabilityToolDescriptor descriptor = descriptor("demo.first", false, false);
        when(catalog.getDeclared("demo.first")).thenReturn(descriptor);

        assertThatThrownBy(() -> createFactory().createToolSpecifications())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate MCP exposed capability");

        properties.setExposedCapabilities(List.of(" "));
        assertThatThrownBy(() -> createFactory().createToolSpecifications())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void shouldFailWhenWhitelistedCapabilityDoesNotExist() {
        properties.setExposedCapabilities(List.of("missing.capability"));
        when(catalog.getDeclared("missing.capability"))
                .thenThrow(new IllegalArgumentException("Capability not found"));

        assertThatThrownBy(() -> createFactory().createToolSpecifications())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capability not found");
    }

    @Test
    void shouldRejectSensitiveCapabilitiesUnlessExplicitlyAllowed() {
        properties.setExposedCapabilities(List.of("demo.write"));
        CapabilityToolDescriptor descriptor = descriptor("demo.write", true, true);
        when(catalog.getDeclared("demo.write")).thenReturn(descriptor);

        assertThatThrownBy(() -> createFactory().createToolSpecifications())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("side-effect capability is not allowed");

        properties.setAllowSideEffects(true);
        assertThatThrownBy(() -> createFactory().createToolSpecifications())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmation-required capability is not allowed");

        properties.setAllowConfirmationRequired(true);
        McpSchema.Tool tool = createFactory().createToolSpecifications().get(0).tool();
        assertThat(tool.annotations().readOnlyHint()).isFalse();
        assertThat(tool.annotations().destructiveHint()).isTrue();
    }

    private AcfMcpToolSpecificationFactory createFactory() {
        return new AcfMcpToolSpecificationFactory(catalog, properties, toolCallHandler);
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
