package cn.iocoder.yudao.framework.mcp.acf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 ACF 能力输出收口为 MCP structuredContent 可直接承载的 JSON 值。
 *
 * <p>MCP 协议边界不应暴露任意 Java 对象。这里使用独立的协议 ObjectMapper，
 * 避免复用管理后台面向浏览器的时间戳序列化规则。</p>
 *
 * @author bujidao
 */
public class AcfMcpStructuredContentNormalizer {

    private final ObjectMapper objectMapper;

    public AcfMcpStructuredContentNormalizer() {
        this(protocolObjectMapper());
    }

    public AcfMcpStructuredContentNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? protocolObjectMapper() : objectMapper;
    }

    public Object normalize(Object value) {
        return normalizeValue(value);
    }

    private Object normalizeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof TemporalAccessor temporal) {
            return temporal.toString();
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            return normalizeIterable(iterable);
        }
        if (value.getClass().isArray()) {
            return normalizeArray(value);
        }
        return normalizeValue(objectMapper.convertValue(value, Object.class));
    }

    private Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), normalizeValue(value)));
        return result;
    }

    private List<Object> normalizeIterable(Iterable<?> source) {
        List<Object> result = new ArrayList<>();
        source.forEach(value -> result.add(normalizeValue(value)));
        return result;
    }

    private List<Object> normalizeArray(Object source) {
        int length = Array.getLength(source);
        List<Object> result = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            result.add(normalizeValue(Array.get(source, index)));
        }
        return result;
    }

    private static ObjectMapper protocolObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }

}
