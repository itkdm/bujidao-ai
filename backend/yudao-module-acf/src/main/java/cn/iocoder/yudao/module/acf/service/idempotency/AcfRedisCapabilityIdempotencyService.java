package cn.iocoder.yudao.module.acf.service.idempotency;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityIdempotencyService;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 基于 Redis 的 ACF 幂等执行记录。
 *
 * 副作用能力在执行前会先抢占同一个幂等键，执行结束后保存最终结果，后续重复请求直接重放结果。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
public class AcfRedisCapabilityIdempotencyService implements CapabilityIdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "acf:idempotency:";

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_UNCERTAIN = "UNCERTAIN";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CapabilityIdempotencyCheck acquire(CapabilityDefinition definition, CapabilityContext context,
                                              String idempotencyKey, String requestDigest) {
        String redisKey = buildRedisKey(definition, context, idempotencyKey);
        StoredRecord processingRecord = new StoredRecord(STATUS_PROCESSING, requestDigest, null, LocalDateTime.now());
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(redisKey, write(processingRecord),
                IDEMPOTENCY_TTL))) {
            return CapabilityIdempotencyCheck.acquired();
        }

        StoredRecord existingRecord = read(redisTemplate.opsForValue().get(redisKey));
        if (existingRecord == null) {
            return CapabilityIdempotencyCheck.error(AcfCapabilityErrorCodes.IDEMPOTENCY_ERROR,
                    "Idempotency record is unreadable");
        }
        if (!requestDigest.equals(existingRecord.requestDigest())) {
            return CapabilityIdempotencyCheck.conflict(AcfCapabilityErrorCodes.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was used with a different request");
        }
        if ((STATUS_COMPLETED.equals(existingRecord.status()) || STATUS_FAILED.equals(existingRecord.status()))
                && existingRecord.result() != null) {
            return CapabilityIdempotencyCheck.replayed(toCapabilityResult(existingRecord.result()));
        }
        return CapabilityIdempotencyCheck.conflict(AcfCapabilityErrorCodes.IDEMPOTENCY_CONFLICT,
                "Idempotency key is already processing");
    }

    @Override
    public void complete(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                         String requestDigest, CapabilityResult result) {
        store(definition, context, idempotencyKey, STATUS_COMPLETED, requestDigest, result);
    }

    @Override
    public void fail(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                     String requestDigest, CapabilityResult result) {
        store(definition, context, idempotencyKey, STATUS_FAILED, requestDigest, result);
    }

    @Override
    public void markUncertain(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                              String requestDigest, CapabilityResult result) {
        store(definition, context, idempotencyKey, STATUS_UNCERTAIN, requestDigest, result);
    }

    @Override
    public void release(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                        String requestDigest) {
        redisTemplate.delete(buildRedisKey(definition, context, idempotencyKey));
    }

    private void store(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey, String status,
                       String requestDigest, CapabilityResult result) {
        StoredRecord record = new StoredRecord(status, requestDigest, toStoredResult(result), LocalDateTime.now());
        redisTemplate.opsForValue().set(buildRedisKey(definition, context, idempotencyKey), write(record),
                IDEMPOTENCY_TTL);
    }

    private String buildRedisKey(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey) {
        String material = nullToDash(context.getTenantId()) + '|'
                + nullToDash(context.getUserId()) + '|'
                + nullToDash(definition.getName()) + '|'
                + nullToDash(definition.getVersion()) + '|'
                + idempotencyKey;
        return KEY_PREFIX + sha256(material);
    }

    private StoredRecord read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredRecord.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String write(StoredRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write ACF idempotency record", exception);
        }
    }

    private StoredCapabilityResult toStoredResult(CapabilityResult result) {
        if (result == null) {
            return null;
        }
        return new StoredCapabilityResult(result.getTraceId(), result.getName(), result.getStatus(), result.getData(),
                result.getErrorCode(), result.getMessage(), result.isRetryable());
    }

    private CapabilityResult toCapabilityResult(StoredCapabilityResult result) {
        return CapabilityResult.builder()
                .traceId(result.traceId())
                .name(result.name())
                .status(result.status())
                .data(result.data())
                .errorCode(result.errorCode())
                .message(result.message())
                .retryable(result.retryable())
                .build();
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private record StoredRecord(String status, String requestDigest, StoredCapabilityResult result,
                                LocalDateTime updatedAt) {
    }

    private record StoredCapabilityResult(String traceId, String name, CapabilityStatus status, Object data,
                                          String errorCode, String message, boolean retryable) {
    }

}
