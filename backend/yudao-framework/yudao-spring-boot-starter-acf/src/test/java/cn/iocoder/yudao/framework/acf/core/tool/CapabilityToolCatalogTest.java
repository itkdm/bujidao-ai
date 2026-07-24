package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityToolCatalogTest {

    @Test
    void shouldListDeclaredToolsWithoutVisibilityContext() {
        CapabilityRegistry capabilityRegistry = mock(CapabilityRegistry.class);
        when(capabilityRegistry.list()).thenReturn(List.of(definition()));

        List<CapabilityToolDescriptor> descriptors = new CapabilityToolCatalog(capabilityRegistry).listDeclared();

        verify(capabilityRegistry).list();
        assertThat(descriptors).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.getCapabilityName()).isEqualTo("erp.stock.query");
            assertThat(descriptor.getVersion()).isEqualTo("1.0.0");
            assertThat(descriptor.getTitle()).isEqualTo("Stock query");
            assertThat(descriptor.getDescription()).isEqualTo("Query current stock by product");
            assertThat(descriptor.getCategory()).isEqualTo("ERP");
            assertThat(descriptor.getPermissionMode()).isEqualTo(CapabilityPermissionMode.ALL);
            assertThat(descriptor.getPermissions()).containsExactly("erp:stock:query");
            assertThat(descriptor.getRiskLevel()).isEqualTo(CapabilityRiskLevel.LOW);
            assertThat(descriptor.isSideEffect()).isFalse();
            assertThat(descriptor.isConfirmationRequired()).isFalse();
            assertThat(descriptor.getInputSchema()).containsEntry("type", "object");
            assertThat(descriptor.getOutputSchema()).containsEntry("type", "object");
        });
        assertThatThrownBy(() -> descriptors.add(descriptors.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldGetDeclaredToolByCapabilityName() {
        CapabilityRegistry capabilityRegistry = mock(CapabilityRegistry.class);
        CapabilityDefinition definition = definition();
        when(capabilityRegistry.get("erp.stock.query")).thenReturn(definition);

        CapabilityToolDescriptor descriptor = new CapabilityToolCatalog(capabilityRegistry)
                .getDeclared("erp.stock.query");

        verify(capabilityRegistry).get("erp.stock.query");
        assertThat(descriptor.getCapabilityName()).isEqualTo(definition.getName());
        assertThat(descriptor.getInputSchema()).isEqualTo(definition.getInputSchema());
        assertThat(descriptor.getOutputSchema()).isEqualTo(definition.getOutputSchema());
    }

    private CapabilityDefinition definition() {
        return CapabilityDefinition.builder()
                .name("erp.stock.query")
                .version("1.0.0")
                .title("Stock query")
                .description("Query current stock by product")
                .category("ERP")
                .permissionMode(CapabilityPermissionMode.ALL)
                .permissions(List.of("erp:stock:query"))
                .riskLevel(CapabilityRiskLevel.LOW)
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "object"))
                .build();
    }

}
