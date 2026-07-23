package cn.iocoder.yudao.framework.acf.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 生成与协议无关的能力请求摘要
 *
 * 摘要只绑定能力名称和完成类型转换后的业务参数。用户、租户等身份归属由确认服务
 * 结合 {@link cn.iocoder.yudao.framework.acf.core.model.CapabilityContext} 独立校验。
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class CapabilityRequestDigestGenerator {

    private final ObjectMapper objectMapper;

    public String generate(String capabilityName, Object arguments) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("name", capabilityName);
        request.set("arguments", objectMapper.valueToTree(arguments));
        try {
            byte[] content = objectMapper.writeValueAsString(sort(request)).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormat.of().formatHex(sha256().digest(content));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize capability request", exception);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.stream().sorted(Comparator.naturalOrder())
                    .forEach(fieldName -> sorted.set(fieldName, sort(node.get(fieldName))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(item -> sorted.add(sort(item)));
            return sorted;
        }
        return node;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

}
