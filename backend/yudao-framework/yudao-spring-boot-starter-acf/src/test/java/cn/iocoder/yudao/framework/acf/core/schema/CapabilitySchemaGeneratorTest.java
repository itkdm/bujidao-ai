package cn.iocoder.yudao.framework.acf.core.schema;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CapabilitySchemaGeneratorTest {

    private final CapabilitySchemaGenerator generator = new CapabilitySchemaGenerator();

    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateSchemaFromCapabilityMetadataAndValidationConstraints() {
        Map<String, Object> schema = generator.generate(ProductQuery.class);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> keyword = (Map<String, Object>) properties.get("keyword");
        Map<String, Object> amount = (Map<String, Object>) properties.get("amount");
        Map<String, Object> statuses = (Map<String, Object>) properties.get("statuses");

        assertEquals("object", schema.get("type"));
        assertEquals(List.of("keyword", "amount"), schema.get("required"));
        assertEquals("商品关键词", keyword.get("description"));
        assertEquals("ACF-001", keyword.get("example"));
        assertEquals(true, keyword.get("x-sensitive"));
        assertEquals(1, keyword.get("minLength"));
        assertEquals(20, keyword.get("maxLength"));
        assertEquals("[A-Z0-9-]+", keyword.get("pattern"));
        assertEquals(new BigDecimal("0"), amount.get("exclusiveMinimum"));
        assertEquals(1, statuses.get("minItems"));
        assertEquals(3, statuses.get("maxItems"));
        assertEquals(List.of("ENABLED", "DISABLED"),
                ((Map<String, Object>) statuses.get("items")).get("enum"));
        assertFalse(properties.containsKey("internalVersion"));
        assertFalse(properties.containsKey("temporaryToken"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveGenericCollectionAndMapValueTypes() throws NoSuchFieldException {
        Type listType = GenericTypes.class.getDeclaredField("items").getGenericType();
        Type mapType = GenericTypes.class.getDeclaredField("counts").getGenericType();

        Map<String, Object> listSchema = generator.generate(listType);
        Map<String, Object> itemSchema = (Map<String, Object>) listSchema.get("items");
        Map<String, Object> itemProperties = (Map<String, Object>) itemSchema.get("properties");
        Map<String, Object> mapSchema = generator.generate(mapType);

        assertEquals("array", listSchema.get("type"));
        assertEquals("integer", ((Map<String, Object>) itemProperties.get("id")).get("type"));
        assertEquals("object", mapSchema.get("type"));
        assertEquals("integer", ((Map<String, Object>) mapSchema.get("additionalProperties")).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleVoidAndRecursiveTypesWithoutUnboundedExpansion() {
        Map<String, Object> voidSchema = generator.generate(Void.TYPE);
        Map<String, Object> nodeSchema = generator.generate(Node.class);
        Map<String, Object> properties = (Map<String, Object>) nodeSchema.get("properties");
        Map<String, Object> childSchema = (Map<String, Object>) properties.get("child");

        assertEquals("object", voidSchema.get("type"));
        assertEquals(Map.of(), voidSchema.get("properties"));
        assertEquals("object", childSchema.get("type"));
        assertFalse(childSchema.containsKey("properties"));
    }

    static class ProductQuery {

        private static final String internalVersion = "1";

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "[A-Z0-9-]+")
        @CapabilityField(description = "商品关键词", example = "ACF-001", sensitive = true)
        private String keyword;

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        private BigDecimal amount;

        @Size(min = 1, max = 3)
        private List<ProductStatus> statuses;

        private transient String temporaryToken;

    }

    enum ProductStatus {
        ENABLED,
        DISABLED
    }

    static class GenericTypes {
        private List<Item> items;
        private Map<String, Long> counts;
    }

    static class Item {
        private Long id;
    }

    static class Node {
        private Node child;
    }

}
