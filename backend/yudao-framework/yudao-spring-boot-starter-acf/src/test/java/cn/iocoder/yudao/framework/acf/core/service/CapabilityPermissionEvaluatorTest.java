package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CapabilityPermissionEvaluatorTest {

    private final PermissionCommonApi permissionCommonApi = mock(PermissionCommonApi.class);
    private final CapabilityPermissionEvaluator evaluator = new CapabilityPermissionEvaluator(permissionCommonApi);

    @Test
    void shouldEvaluateAnyPermissionsInSingleCall() {
        CapabilityDefinition definition = definition(CapabilityPermissionMode.ANY,
                List.of("product:query", "product:export"));
        when(permissionCommonApi.hasAnyPermissions(10L, "product:query", "product:export")).thenReturn(true);

        boolean allowed = evaluator.hasPermission(10L, definition);

        assertThat(allowed).isTrue();
        verify(permissionCommonApi).hasAnyPermissions(10L, "product:query", "product:export");
    }

    @Test
    void shouldEvaluateAllPermissionsWithShortCircuit() {
        CapabilityDefinition definition = definition(CapabilityPermissionMode.ALL,
                List.of("product:query", "product:export", "product:update"));
        when(permissionCommonApi.hasAnyPermissions(10L, "product:query")).thenReturn(true);
        when(permissionCommonApi.hasAnyPermissions(10L, "product:export")).thenReturn(false);

        boolean allowed = evaluator.hasPermission(10L, definition);

        assertThat(allowed).isFalse();
        verify(permissionCommonApi).hasAnyPermissions(10L, "product:query");
        verify(permissionCommonApi).hasAnyPermissions(10L, "product:export");
        verify(permissionCommonApi, never()).hasAnyPermissions(10L, "product:update");
    }

    @Test
    void shouldAllowWhenAllPermissionsAreGranted() {
        CapabilityDefinition definition = definition(CapabilityPermissionMode.ALL,
                List.of("product:query", "product:export"));
        when(permissionCommonApi.hasAnyPermissions(10L, "product:query")).thenReturn(true);
        when(permissionCommonApi.hasAnyPermissions(10L, "product:export")).thenReturn(true);

        boolean allowed = evaluator.hasPermission(10L, definition);

        assertThat(allowed).isTrue();
        verify(permissionCommonApi).hasAnyPermissions(10L, "product:query");
        verify(permissionCommonApi).hasAnyPermissions(10L, "product:export");
    }

    @Test
    void shouldRejectIncompletePermissionInputWithoutCallingPermissionApi() {
        assertThat(evaluator.hasPermission(null, definition(CapabilityPermissionMode.ALL,
                List.of("product:query")))).isFalse();
        assertThat(evaluator.hasPermission(10L, null)).isFalse();
        assertThat(evaluator.hasPermission(10L, definition(CapabilityPermissionMode.ALL, List.of()))).isFalse();

        verifyNoInteractions(permissionCommonApi);
    }

    private CapabilityDefinition definition(CapabilityPermissionMode mode, List<String> permissions) {
        return CapabilityDefinition.builder()
                .name("test.product.search")
                .permissions(permissions)
                .permissionMode(mode)
                .build();
    }

}
