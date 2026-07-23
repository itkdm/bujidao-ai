package cn.iocoder.yudao.framework.acf.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRequestDigestGeneratorTest {

    private final CapabilityRequestDigestGenerator generator =
            new CapabilityRequestDigestGenerator(new ObjectMapper());

    @Test
    void shouldGenerateSameDigestForDifferentObjectFieldOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("keyword", "keyboard");
        first.put("filters", Map.of("enabled", true, "limit", 10));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("filters", Map.of("limit", 10, "enabled", true));
        second.put("keyword", "keyboard");

        assertThat(generator.generate("test.product.search", first))
                .isEqualTo(generator.generate("test.product.search", second))
                .startsWith("sha256:");
    }

    @Test
    void shouldBindDigestToCapabilityNameAndArguments() {
        String digest = generator.generate("test.product.search", Map.of("ids", List.of(1, 2)));

        assertThat(generator.generate("test.product.update", Map.of("ids", List.of(1, 2))))
                .isNotEqualTo(digest);
        assertThat(generator.generate("test.product.search", Map.of("ids", List.of(2, 1))))
                .isNotEqualTo(digest);
    }

}
