package cn.iocoder.yudao.framework.acf.core.schema;

import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 将 Java 类型转换为能力输入、输出使用的 JSON Schema
 *
 * @author bujidao
 */
public class CapabilitySchemaGenerator {

    /** 限制对象递归展开层数，避免复杂 DTO 生成过大的 Schema */
    private static final int MAX_DEPTH = 3;

    /**
     * 生成指定 Java 类型的 JSON Schema
     *
     * @param type Java 类型，支持带泛型信息的 {@link Type}
     * @return JSON Schema
     */
    public Map<String, Object> generate(Type type) {
        return generate(type, 0, new LinkedHashSet<>());
    }

    private Map<String, Object> generate(Type type, int depth, Set<Type> visiting) {
        // 无入参能力仍使用空对象 Schema，便于后续统一作为 Tool 的 parameters 输出。
        if (type == null || type == Void.TYPE || type == Void.class) {
            return emptyObjectSchema();
        }
        // 优先处理携带泛型信息的 Type，避免 List<Item> 退化成无法识别元素类型的原始 List。
        if (type instanceof GenericArrayType arrayType) {
            return arraySchema(generate(arrayType.getGenericComponentType(), depth + 1, visiting));
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return generateParameterized(parameterizedType, depth, visiting);
        }
        if (type instanceof WildcardType wildcardType) {
            return generateBound(wildcardType.getUpperBounds(), depth, visiting);
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            return generateBound(typeVariable.getBounds(), depth, visiting);
        }
        // 无法可靠推断的类型返回开放 Schema，不虚构可能限制真实数据的字段结构。
        if (!(type instanceof Class<?> typeClass)) {
            return new LinkedHashMap<>();
        }
        return generateClass(typeClass, depth, visiting);
    }

    private Map<String, Object> generateParameterized(ParameterizedType type, int depth, Set<Type> visiting) {
        if (!(type.getRawType() instanceof Class<?> rawClass)) {
            return new LinkedHashMap<>();
        }
        Type[] arguments = type.getActualTypeArguments();
        if (Collection.class.isAssignableFrom(rawClass)) {
            Type itemType = arguments.length == 1 ? arguments[0] : Object.class;
            return arraySchema(generate(itemType, depth + 1, visiting));
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            // JSON 对象的键固定为字符串，因此只需要保留 Map 的值类型约束。
            Type valueType = arguments.length == 2 ? arguments[1] : Object.class;
            return mapSchema(generate(valueType, depth + 1, visiting));
        }
        return generateClass(rawClass, depth, visiting);
    }

    private Map<String, Object> generateBound(Type[] bounds, int depth, Set<Type> visiting) {
        if (bounds.length == 0 || bounds[0] == Object.class) {
            return new LinkedHashMap<>();
        }
        return generate(bounds[0], depth, visiting);
    }

    private Map<String, Object> generateClass(Class<?> type, int depth, Set<Type> visiting) {
        if (type == Object.class) {
            return new LinkedHashMap<>();
        }
        if (type.isArray()) {
            return arraySchema(generate(type.getComponentType(), depth + 1, visiting));
        }
        if (type.isEnum()) {
            return enumSchema(type);
        }
        String simpleType = getSimpleJsonType(type);
        if (simpleType != null) {
            return simpleSchema(simpleType, getFormat(type));
        }
        if (Collection.class.isAssignableFrom(type)) {
            return arraySchema(new LinkedHashMap<>());
        }
        if (Map.class.isAssignableFrom(type)) {
            return mapSchema(new LinkedHashMap<>());
        }
        // visiting 只记录当前递归路径：既阻止自引用死循环，也允许同一类型出现在不同兄弟字段中。
        if (depth >= MAX_DEPTH || !visiting.add(type)) {
            return objectSchema();
        }
        try {
            return generateObject(type, depth, visiting);
        } finally {
            visiting.remove(type);
        }
    }

    private Map<String, Object> generateObject(Class<?> type, int depth, Set<Type> visiting) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Field field : getFields(type)) {
            if (shouldSkip(field)) {
                continue;
            }
            // 先生成类型结构，再叠加字段自身的说明和校验约束，避免元数据影响类型递归。
            Map<String, Object> fieldSchema = generate(field.getGenericType(), depth + 1, visiting);
            applyMetadata(fieldSchema, field);
            applyValidation(fieldSchema, field);
            properties.put(field.getName(), fieldSchema);
            if (isRequired(field)) {
                required.add(field.getName());
            }
        }

        Map<String, Object> schema = objectSchema();
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private List<Field> getFields(Class<?> type) {
        // 按父类到子类的顺序收集字段，保证继承 DTO 的 Schema 输出稳定且符合阅读顺序。
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.addFirst(current);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    private boolean shouldSkip(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic();
    }

    private boolean isRequired(Field field) {
        return field.isAnnotationPresent(NotNull.class)
                || field.isAnnotationPresent(NotBlank.class)
                || field.isAnnotationPresent(NotEmpty.class);
    }

    private void applyMetadata(Map<String, Object> schema, Field field) {
        CapabilityField metadata = field.getAnnotation(CapabilityField.class);
        if (metadata == null) {
            return;
        }
        if (!metadata.description().isBlank()) {
            schema.put("description", metadata.description());
        }
        if (!metadata.example().isBlank()) {
            schema.put("example", metadata.example());
        }
        if (metadata.sensitive()) {
            schema.put("x-sensitive", true);
        }
    }

    private void applyValidation(Map<String, Object> schema, Field field) {
        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            schema.put("minimum", min.value());
        }
        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            schema.put("maximum", max.value());
        }
        DecimalMin decimalMin = field.getAnnotation(DecimalMin.class);
        if (decimalMin != null) {
            schema.put(decimalMin.inclusive() ? "minimum" : "exclusiveMinimum", new BigDecimal(decimalMin.value()));
        }
        DecimalMax decimalMax = field.getAnnotation(DecimalMax.class);
        if (decimalMax != null) {
            schema.put(decimalMax.inclusive() ? "maximum" : "exclusiveMaximum", new BigDecimal(decimalMax.value()));
        }
        applySignConstraints(schema, field);
        applySizeConstraints(schema, field);
        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null) {
            schema.put("pattern", pattern.regexp());
        }
    }

    private void applySignConstraints(Map<String, Object> schema, Field field) {
        if (field.isAnnotationPresent(Positive.class)) {
            schema.put("exclusiveMinimum", 0);
        } else if (field.isAnnotationPresent(PositiveOrZero.class)) {
            schema.put("minimum", 0);
        }
        if (field.isAnnotationPresent(Negative.class)) {
            schema.put("exclusiveMaximum", 0);
        } else if (field.isAnnotationPresent(NegativeOrZero.class)) {
            schema.put("maximum", 0);
        }
    }

    private void applySizeConstraints(Map<String, Object> schema, Field field) {
        int minimum = 0;
        int maximum = Integer.MAX_VALUE;
        Size size = field.getAnnotation(Size.class);
        if (size != null) {
            minimum = size.min();
            maximum = size.max();
        }
        if (field.isAnnotationPresent(NotBlank.class) || field.isAnnotationPresent(NotEmpty.class)) {
            minimum = Math.max(minimum, 1);
        }
        if (minimum == 0 && maximum == Integer.MAX_VALUE) {
            return;
        }

        // JSON Schema 对字符串、数组和对象分别使用不同的长度关键字，不能统一映射为 minLength。
        Class<?> fieldType = field.getType();
        if (CharSequence.class.isAssignableFrom(fieldType)) {
            putRange(schema, "minLength", "maxLength", minimum, maximum);
        } else if (fieldType.isArray() || Collection.class.isAssignableFrom(fieldType)) {
            putRange(schema, "minItems", "maxItems", minimum, maximum);
        } else if (Map.class.isAssignableFrom(fieldType)) {
            putRange(schema, "minProperties", "maxProperties", minimum, maximum);
        }
    }

    private void putRange(Map<String, Object> schema, String minimumKey, String maximumKey,
                          int minimum, int maximum) {
        if (minimum > 0) {
            schema.put(minimumKey, minimum);
        }
        if (maximum < Integer.MAX_VALUE) {
            schema.put(maximumKey, maximum);
        }
    }

    private String getSimpleJsonType(Class<?> type) {
        if (type == byte.class || type == short.class || type == int.class || type == long.class
                || type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == BigInteger.class) {
            return "integer";
        }
        if (type == float.class || type == double.class || type == Float.class || type == Double.class
                || type == BigDecimal.class || Number.class.isAssignableFrom(type)) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == char.class || type == Character.class || CharSequence.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type) || Temporal.class.isAssignableFrom(type) || type == UUID.class) {
            return "string";
        }
        return null;
    }

    private String getFormat(Class<?> type) {
        if (type == LocalDate.class) {
            return "date";
        }
        if (type == LocalDateTime.class || type == OffsetDateTime.class || type == ZonedDateTime.class
                || Date.class.isAssignableFrom(type)) {
            return "date-time";
        }
        if (type == UUID.class) {
            return "uuid";
        }
        return null;
    }

    private Map<String, Object> enumSchema(Class<?> type) {
        Map<String, Object> schema = simpleSchema("string", null);
        schema.put("enum", Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
        return schema;
    }

    private Map<String, Object> simpleSchema(String type, String format) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (format != null) {
            schema.put("format", format);
        }
        return schema;
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        Map<String, Object> schema = simpleSchema("array", null);
        schema.put("items", items);
        return schema;
    }

    private Map<String, Object> mapSchema(Map<String, Object> values) {
        Map<String, Object> schema = objectSchema();
        schema.put("additionalProperties", values);
        return schema;
    }

    private Map<String, Object> emptyObjectSchema() {
        Map<String, Object> schema = objectSchema();
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    private Map<String, Object> objectSchema() {
        return simpleSchema("object", null);
    }

}
