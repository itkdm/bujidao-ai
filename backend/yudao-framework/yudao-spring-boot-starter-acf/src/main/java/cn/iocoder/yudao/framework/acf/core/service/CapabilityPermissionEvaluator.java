package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 根据芋道权限体系校验用户是否具备能力声明的权限
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityPermissionEvaluator {

    private final PermissionCommonApi permissionCommonApi;

    public boolean hasPermission(Long userId, CapabilityDefinition definition) {
        if (userId == null || definition == null) {
            return false;
        }
        List<String> permissions = definition.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        if (definition.getPermissionMode() == CapabilityPermissionMode.ANY) {
            return permissionCommonApi.hasAnyPermissions(userId, permissions.toArray(String[]::new));
        }
        // PermissionCommonApi 只提供 ANY 查询；ALL 模式逐项短路，避免无必要的后续权限查询。
        return permissions.stream().allMatch(permission -> permissionCommonApi.hasAnyPermissions(userId, permission));
    }

}
