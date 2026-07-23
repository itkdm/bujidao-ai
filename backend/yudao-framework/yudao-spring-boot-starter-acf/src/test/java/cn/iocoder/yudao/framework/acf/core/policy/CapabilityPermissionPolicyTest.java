package cn.iocoder.yudao.framework.acf.core.policy;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityPermissionEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CapabilityPermissionPolicyTest {

    private final CapabilityPermissionEvaluator permissionEvaluator = mock(CapabilityPermissionEvaluator.class);
    private final CapabilityPermissionPolicy policy = new CapabilityPermissionPolicy(permissionEvaluator);
    private final CapabilityDefinition definition = CapabilityDefinition.builder()
            .name("test.product.search")
            .build();

    @Test
    void shouldSupportExecutionAndVisibilityPhases() {
        assertThat(policy.supports(CapabilityPolicyPhase.EXECUTION)).isTrue();
        assertThat(policy.supports(CapabilityPolicyPhase.VISIBILITY)).isTrue();
        assertThat(policy.supports(CapabilityPolicyPhase.RELEASE_READINESS)).isFalse();
    }

    @Test
    void shouldDenyExecutionWithoutAuthenticatedUser() {
        CapabilityPolicyDecision decision = policy.evaluate(context(CapabilityContext.empty()));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getErrorCode()).isEqualTo(CapabilityPermissionPolicy.ERROR_PERMISSION_DENIED);
        assertThat(decision.getReason()).isEqualTo("Authenticated user is required");
        verifyNoInteractions(permissionEvaluator);
    }

    @Test
    void shouldDenyExecutionWithoutRequiredPermission() {
        CapabilityContext invocationContext = CapabilityContext.builder().userId(10L).build();
        when(permissionEvaluator.hasPermission(10L, definition)).thenReturn(false);

        CapabilityPolicyDecision decision = policy.evaluate(context(invocationContext));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getErrorCode()).isEqualTo(CapabilityPermissionPolicy.ERROR_PERMISSION_DENIED);
        assertThat(decision.getReason()).isEqualTo("No permission for capability");
        verify(permissionEvaluator).hasPermission(10L, definition);
    }

    @Test
    void shouldAllowExecutionWithRequiredPermission() {
        CapabilityContext invocationContext = CapabilityContext.builder().userId(10L).build();
        when(permissionEvaluator.hasPermission(10L, definition)).thenReturn(true);

        CapabilityPolicyDecision decision = policy.evaluate(context(invocationContext));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReason()).isEqualTo("permission allowed");
    }

    private CapabilityPolicyContext context(CapabilityContext invocationContext) {
        return new CapabilityPolicyContext(CapabilityPolicyPhase.EXECUTION, definition, invocationContext);
    }

}
