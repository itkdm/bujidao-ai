package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityVisibilityServiceTest {

    private final CapabilityRegistry capabilityRegistry = mock(CapabilityRegistry.class);
    private final CapabilityGovernanceService governanceService = mock(CapabilityGovernanceService.class);
    private final CapabilityVisibilityService visibilityService = new CapabilityVisibilityService(
            capabilityRegistry, governanceService);

    @Test
    void shouldFilterMetadataBeforeEvaluatingVisibilityAndReturnEffectiveDefinition() {
        CapabilityDefinition matched = definition("erp.product.search", "erp", CapabilityRiskLevel.LOW, false);
        CapabilityDefinition categoryMismatch = definition("system.user.search", "system",
                CapabilityRiskLevel.LOW, false);
        CapabilityDefinition riskMismatch = definition("erp.product.delete", "erp",
                CapabilityRiskLevel.HIGH, true);
        CapabilityDefinition effective = matched.toBuilder().title("租户商品查询").build();
        CapabilityContext context = CapabilityContext.builder().userId(10L).tenantId(20L).build();
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .category("erp")
                .riskLevel(CapabilityRiskLevel.LOW)
                .sideEffect(false)
                .context(context)
                .build();
        when(capabilityRegistry.list()).thenReturn(List.of(matched, categoryMismatch, riskMismatch));
        when(governanceService.evaluateVisibility(matched, context))
                .thenReturn(CapabilityPolicyResult.allow(effective, List.of()));

        List<CapabilityDefinition> result = visibilityService.listVisible(query);

        assertThat(result).containsExactly(effective);
        verify(governanceService).evaluateVisibility(matched, context);
        verify(governanceService, never()).evaluateVisibility(categoryMismatch, context);
        verify(governanceService, never()).evaluateVisibility(riskMismatch, context);
    }

    @Test
    void shouldExcludeCapabilityDeniedByVisibilityGovernance() {
        CapabilityDefinition denied = definition("erp.product.delete", "erp", CapabilityRiskLevel.HIGH, true);
        CapabilityDefinition allowed = definition("erp.product.search", "erp", CapabilityRiskLevel.LOW, false);
        CapabilityContext context = CapabilityContext.builder().userId(10L).build();
        when(capabilityRegistry.list()).thenReturn(List.of(denied, allowed));
        when(governanceService.evaluateVisibility(denied, context))
                .thenReturn(CapabilityPolicyResult.deny(denied, "PERMISSION_DENIED", "denied", List.of()));
        when(governanceService.evaluateVisibility(allowed, context))
                .thenReturn(CapabilityPolicyResult.allow(allowed, List.of()));

        List<CapabilityDefinition> result = visibilityService.listVisible(
                CapabilityVisibilityQuery.builder().context(context).build());

        assertThat(result).containsExactly(allowed);
    }

    @Test
    void shouldReturnOptionalForVisibleCapability() {
        CapabilityDefinition definition = definition("erp.product.search", "erp", CapabilityRiskLevel.LOW, false);
        CapabilityContext context = CapabilityContext.builder().userId(10L).build();
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder().context(context).build();
        when(capabilityRegistry.get(definition.getName())).thenReturn(definition);
        when(governanceService.evaluateVisibility(definition, context))
                .thenReturn(CapabilityPolicyResult.allow(definition, List.of()));

        Optional<CapabilityDefinition> visible = visibilityService.getVisible(definition.getName(), query);

        assertThat(visible).contains(definition);
    }

    @Test
    void shouldReturnEmptyOptionalForInvisibleCapability() {
        CapabilityDefinition definition = definition("erp.product.delete", "erp", CapabilityRiskLevel.HIGH, true);
        CapabilityContext context = CapabilityContext.builder().userId(10L).build();
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder().context(context).build();
        when(capabilityRegistry.get(definition.getName())).thenReturn(definition);
        when(governanceService.evaluateVisibility(definition, context))
                .thenReturn(CapabilityPolicyResult.deny(definition, "PERMISSION_DENIED", "denied", List.of()));

        Optional<CapabilityDefinition> visible = visibilityService.getVisible(definition.getName(), query);

        assertThat(visible).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptionalWhenCapabilityDoesNotMatchQuery() {
        CapabilityDefinition definition = definition("erp.product.delete", "erp", CapabilityRiskLevel.HIGH, true);
        CapabilityContext context = CapabilityContext.builder().userId(10L).build();
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .riskLevel(CapabilityRiskLevel.LOW)
                .context(context)
                .build();
        when(capabilityRegistry.get(definition.getName())).thenReturn(definition);

        Optional<CapabilityDefinition> visible = visibilityService.getVisible(definition.getName(), query);

        assertThat(visible).isEmpty();
        verify(governanceService, never()).evaluateVisibility(definition, context);
    }

    private CapabilityDefinition definition(String name, String category, CapabilityRiskLevel riskLevel,
                                            boolean sideEffect) {
        return CapabilityDefinition.builder()
                .name(name)
                .title(name)
                .description(name)
                .category(category)
                .riskLevel(riskLevel)
                .sideEffect(sideEffect)
                .build();
    }

}
