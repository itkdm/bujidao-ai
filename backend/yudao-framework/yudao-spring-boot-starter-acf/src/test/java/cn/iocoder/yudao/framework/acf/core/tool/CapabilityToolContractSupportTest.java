package cn.iocoder.yudao.framework.acf.core.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityToolContractSupportTest {

    private final CapabilityToolContractSupport support = new CapabilityToolContractSupport();

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeControlFieldsAndRequireIdempotencyWhenNeeded() {
        Map<String, Object> schema = support.enrichInputSchema(Map.of(
                "type", "object",
                "properties", Map.of("customerId", Map.of("type", "integer")),
                "required", List.of("customerId"),
                "additionalProperties", false), true);

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("customerId",
                CapabilityToolContractSupport.CLIENT_REQUEST_ID,
                CapabilityToolContractSupport.IDEMPOTENCY_KEY,
                CapabilityToolContractSupport.CONFIRMATION_TOKEN);
        assertThat((List<String>) schema.get("required"))
                .containsExactly("customerId", CapabilityToolContractSupport.IDEMPOTENCY_KEY);
        assertThat(properties.get(CapabilityToolContractSupport.IDEMPOTENCY_KEY).toString())
                .contains("Required for side-effecting");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepIdempotencyOptionalForReadOnlyTools() {
        Map<String, Object> schema = support.enrichInputSchema(Map.of(
                "type", "object",
                "properties", Map.of()), false);

        assertThat((Map<String, Object>) schema.get("properties"))
                .containsKey(CapabilityToolContractSupport.IDEMPOTENCY_KEY);
        assertThat(schema).doesNotContainKey("required");
    }

    @Test
    void shouldResolveControlFieldsAndRemoveThemFromBusinessArguments() {
        CapabilityToolInvocationInput input = support.resolveInvocationInput(Map.of(
                "customerId", 10L,
                CapabilityToolContractSupport.CLIENT_REQUEST_ID, " request-1 ",
                CapabilityToolContractSupport.IDEMPOTENCY_KEY, " idem-1 ",
                CapabilityToolContractSupport.CONFIRMATION_TOKEN, " confirm-1 "));

        assertThat(input.getBusinessArguments()).containsExactlyEntriesOf(Map.of("customerId", 10L));
        assertThat(input.getClientRequestId()).isEqualTo("request-1");
        assertThat(input.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(input.getConfirmationToken()).isEqualTo("confirm-1");
    }

    @Test
    void shouldRejectNonStringControlField() {
        assertThatThrownBy(() -> support.resolveInvocationInput(Map.of(
                CapabilityToolContractSupport.IDEMPOTENCY_KEY, 123)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
    }

}
