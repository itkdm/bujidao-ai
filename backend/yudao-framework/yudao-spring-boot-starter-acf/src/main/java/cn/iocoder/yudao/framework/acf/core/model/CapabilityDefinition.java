package cn.iocoder.yudao.framework.acf.core.model;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 已注册能力的声明与 Schema 定义
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityDefinition {

    private final String name;
    private final String title;
    private final String description;
    private final String category;
    private final List<String> permissions;
    private final CapabilityPermissionMode permissionMode;
    private final CapabilityRiskLevel riskLevel;
    private final boolean sideEffect;
    private final boolean confirmationRequired;
    private final String version;
    private final int timeoutMs;
    private final Type argumentType;
    private final Type returnType;
    private final Map<String, Object> inputSchema;
    private final Map<String, Object> outputSchema;

}
