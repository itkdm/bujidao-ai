package cn.iocoder.yudao.framework.acf.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityDefinitionTest {

    @Test
    void shouldDefensivelyCopyPermissionsAndNestedSchemas() {
        List<String> permissions = new ArrayList<>(List.of("order:read"));
        List<Object> required = new ArrayList<>(List.of("orderId"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("required", required);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("properties", nested);

        CapabilityDefinition definition = CapabilityDefinition.builder()
                .name("test.order.read")
                .permissions(permissions)
                .inputSchema(schema)
                .outputSchema(schema)
                .build();

        permissions.add("order:write");
        required.add("tenantId");
        nested.put("type", "object");
        schema.put("secret", true);

        assertThat(definition.getPermissions()).containsExactly("order:read");
        assertThat(definition.getInputSchema()).doesNotContainKey("secret");
        Map<Object, Object> frozenNested = (Map<Object, Object>) definition.getInputSchema().get("properties");
        List<Object> frozenRequired = (List<Object>) frozenNested.get("required");
        assertThat(frozenNested).doesNotContainKey("type");
        assertThat(frozenRequired).containsExactly("orderId");

        assertThatThrownBy(() -> definition.getPermissions().add("forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.getInputSchema().put("forbidden", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> frozenNested.put("forbidden", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> frozenRequired.add("forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);

        CapabilityDefinition rebuilt = definition.toBuilder().build();
        assertThat(rebuilt.getInputSchema()).isEqualTo(definition.getInputSchema());
        assertThatThrownBy(() -> rebuilt.getOutputSchema().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
