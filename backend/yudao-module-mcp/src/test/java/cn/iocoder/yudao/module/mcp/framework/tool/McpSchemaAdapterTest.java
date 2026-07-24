package cn.iocoder.yudao.module.mcp.framework.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpSchemaAdapterTest {

    @Test
    void shouldCreateEmptyObjectSchemaForMissingInput() {
        assertThat(McpSchemaAdapter.adaptInputSchema(Map.of())).containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(McpSchemaAdapter.adaptInputSchema(Map.of()).get("properties"))
                .isEqualTo(Map.of());
    }

    @Test
    void shouldPreserveObjectInputWithoutSharingNestedCollections() {
        List<String> required = new ArrayList<>(List.of("name"));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "object");
        source.put("properties", Map.of("name", Map.of("type", "string")));
        source.put("required", required);

        Map<String, Object> adapted = McpSchemaAdapter.adaptInputSchema(source);
        required.add("later");

        assertThat(adapted).isNotSameAs(source);
        assertThat(adapted.get("required")).isEqualTo(List.of("name"));
    }

    @Test
    void shouldWrapScalarAndArraySchemas() {
        Map<String, Object> input = McpSchemaAdapter.adaptInputSchema(Map.of("type", "string"));
        Map<String, Object> output = McpSchemaAdapter.adaptOutputSchema(
                Map.of("type", "array", "items", Map.of("type", "integer")));

        assertThat(input).containsEntry("required", List.of(McpSchemaAdapter.INPUT_VALUE_PROPERTY));
        assertThat(((Map<?, ?>) input.get("properties"))
                .containsKey(McpSchemaAdapter.INPUT_VALUE_PROPERTY)).isTrue();
        assertThat(output).containsEntry("required", List.of(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY));
        assertThat(((Map<?, ?>) output.get("properties"))
                .containsKey(McpSchemaAdapter.OUTPUT_RESULT_PROPERTY)).isTrue();
    }

    @Test
    void shouldCreateEmptyObjectSchemaForVoidOutput() {
        assertThat(McpSchemaAdapter.adaptOutputSchema(Map.of("type", "null")))
                .containsEntry("type", "object")
                .containsEntry("properties", Map.of());
    }

}
