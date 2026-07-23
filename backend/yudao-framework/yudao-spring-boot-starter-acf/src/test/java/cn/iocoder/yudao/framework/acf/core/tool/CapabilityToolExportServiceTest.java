package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityVisibilityService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityToolExportServiceTest {

    @Test
    void shouldExportGovernedVisibleDefinitionsWithoutProtocolCoupling() {
        CapabilityVisibilityService visibilityService = mock(CapabilityVisibilityService.class);
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .category("ERP")
                .riskLevel(CapabilityRiskLevel.LOW)
                .sideEffect(false)
                .build();
        when(visibilityService.listVisible(same(query))).thenReturn(List.of(definition()));

        List<CapabilityToolDescriptor> descriptors = new CapabilityToolExportService(visibilityService)
                .export(query);

        verify(visibilityService).listVisible(same(query));
        assertThat(descriptors).hasSize(1);
        CapabilityToolDescriptor descriptor = descriptors.get(0);
        assertThat(descriptor.getCapabilityName()).isEqualTo("erp.stock.query");
        assertThat(descriptor.getVersion()).isEqualTo("1.0.0");
        assertThat(descriptor.getTitle()).isEqualTo("查询库存");
        assertThat(descriptor.getDescription()).isEqualTo("按商品查询当前库存");
        assertThat(descriptor.getCategory()).isEqualTo("ERP");
        assertThat(descriptor.getPermissionMode()).isEqualTo(CapabilityPermissionMode.ALL);
        assertThat(descriptor.getPermissions()).containsExactly("erp:stock:query");
        assertThat(descriptor.getRiskLevel()).isEqualTo(CapabilityRiskLevel.LOW);
        assertThat(descriptor.isSideEffect()).isFalse();
        assertThat(descriptor.isConfirmationRequired()).isFalse();
        assertThat(descriptor.isIdempotencyRequired()).isFalse();
        assertThat(descriptor.getInputSchema()).containsEntry("type", "object");
        assertThat(descriptor.getOutputSchema()).containsEntry("type", "object");
    }

    @Test
    void shouldProtectExportedContractFromExternalMutation() {
        List<String> required = new ArrayList<>(List.of("productId"));
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("required", required);
        CapabilityToolDescriptor descriptor = CapabilityToolDescriptor.builder()
                .capabilityName("erp.stock.update")
                .inputSchema(inputSchema)
                .permissions(new ArrayList<>(List.of("erp:stock:update")))
                .sideEffect(true)
                .build();

        required.add("warehouseId");
        inputSchema.put("additionalProperties", false);

        assertThat(descriptor.getInputSchema()).doesNotContainKey("additionalProperties");
        assertThat(descriptor.getInputSchema().get("required")).isEqualTo(List.of("productId"));
        assertThat(descriptor.isIdempotencyRequired()).isTrue();
        assertThatThrownBy(() -> descriptor.getInputSchema().put("title", "mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> descriptor.getPermissions().add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CapabilityDefinition definition() {
        return CapabilityDefinition.builder()
                .name("erp.stock.query")
                .version("1.0.0")
                .title("查询库存")
                .description("按商品查询当前库存")
                .category("ERP")
                .permissionMode(CapabilityPermissionMode.ALL)
                .permissions(List.of("erp:stock:query"))
                .riskLevel(CapabilityRiskLevel.LOW)
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "object"))
                .build();
    }

}
