package cn.iocoder.yudao.framework.acf.core.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 能力执行后建议调用方继续执行的标准能力动作
 *
 * name 使用 capability name，不使用具体协议生成的 tool name。
 *
 * @author bujidao
 */
@Getter
@Builder
public final class CapabilityNextAction {

    private final String name;
    private final String title;
    private final Object arguments;

    public static CapabilityNextAction of(String name, String title, Object arguments) {
        return CapabilityNextAction.builder()
                .name(name)
                .title(title)
                .arguments(arguments)
                .build();
    }

}
