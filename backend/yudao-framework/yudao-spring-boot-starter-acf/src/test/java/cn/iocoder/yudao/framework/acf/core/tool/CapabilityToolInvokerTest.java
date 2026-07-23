package cn.iocoder.yudao.framework.acf.core.tool;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityToolInvokerTest {

    @Test
    void shouldDelegateToolCallToGovernedCapabilityExecutor() {
        CapabilityExecutor executor = mock(CapabilityExecutor.class);
        ArgumentCaptor<CapabilityInvokeCommand> commandCaptor = ArgumentCaptor.forClass(CapabilityInvokeCommand.class);
        CapabilityResult expected = CapabilityResult.success("erp.stock.query", Map.of("quantity", 10));
        when(executor.invoke(commandCaptor.capture())).thenReturn(expected);
        CapabilityContext context = CapabilityContext.builder()
                .userId(1L)
                .tenantId(2L)
                .source("AGENT_TOOL")
                .consumerType(CapabilityConsumerType.AGENT)
                .consumerId("agent-001")
                .clientRequestId("request-001")
                .build();
        CapabilityToolCall call = CapabilityToolCall.builder()
                .capabilityName("erp.stock.query")
                .arguments(Map.of("productId", 100L))
                .context(context)
                .idempotencyKey("idem-001")
                .confirmationToken("confirm-001")
                .build();

        CapabilityResult result = new CapabilityToolInvoker(executor).invoke(call);

        assertThat(result).isSameAs(expected);
        CapabilityInvokeCommand command = commandCaptor.getValue();
        assertThat(command.getName()).isEqualTo("erp.stock.query");
        assertThat(command.getArguments()).isEqualTo(Map.of("productId", 100L));
        assertThat(command.getContext()).isSameAs(context);
        assertThat(command.getIdempotencyKey()).isEqualTo("idem-001");
        assertThat(command.getConfirmationToken()).isEqualTo("confirm-001");
        verify(executor).invoke(command);
    }

    @Test
    void shouldRouteMissingCallThroughStandardValidation() {
        CapabilityExecutor executor = mock(CapabilityExecutor.class);
        ArgumentCaptor<CapabilityInvokeCommand> commandCaptor = ArgumentCaptor.forClass(CapabilityInvokeCommand.class);
        CapabilityResult expected = CapabilityResult.failure(null, "BAD_REQUEST", "Capability name is required");
        when(executor.invoke(commandCaptor.capture())).thenReturn(expected);

        CapabilityResult result = new CapabilityToolInvoker(executor).invoke(null);

        assertThat(result).isSameAs(expected);
        assertThat(commandCaptor.getValue().getName()).isNull();
    }

}
