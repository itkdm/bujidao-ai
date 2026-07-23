package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityExecutor;
import lombok.RequiredArgsConstructor;

/**
 * 协议无关的工具调用入口
 *
 * 具体 Agent 或协议模块只负责解析自身请求并构造 {@link CapabilityToolCall}，权限、确认、
 * 幂等、运行时保护和审计仍统一由 {@link CapabilityExecutor} 执行，避免适配器绕过 ACF 治理。
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityToolInvoker {

    private final CapabilityExecutor capabilityExecutor;

    public CapabilityResult invoke(CapabilityToolCall call) {
        CapabilityInvokeCommand command = call == null
                ? CapabilityInvokeCommand.builder().build()
                : CapabilityInvokeCommand.builder()
                        .name(call.getCapabilityName())
                        .arguments(call.getArguments())
                        .context(call.getContext())
                        .idempotencyKey(call.getIdempotencyKey())
                        .confirmationToken(call.getConfirmationToken())
                        .build();
        return capabilityExecutor.invoke(command);
    }

}
