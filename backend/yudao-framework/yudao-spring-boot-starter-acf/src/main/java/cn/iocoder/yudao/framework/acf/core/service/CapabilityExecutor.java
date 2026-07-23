package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ACF 基础能力执行器
 *
 * 负责执行调用前治理，把外部参数转换为能力方法声明的 Java 类型，
 * 完成 Bean Validation 校验并调用已注册方法。
 *
 * @author bujidao
 */
public class CapabilityExecutor {

    public static final String ERROR_BAD_REQUEST = AcfCapabilityErrorCodes.BAD_REQUEST;
    public static final String ERROR_CAPABILITY_NOT_FOUND = AcfCapabilityErrorCodes.CAPABILITY_NOT_FOUND;
    public static final String ERROR_POLICY = AcfCapabilityErrorCodes.POLICY_ERROR;
    public static final String ERROR_POLICY_DENIED = AcfCapabilityErrorCodes.POLICY_DENIED;
    public static final String ERROR_CONFIRMATION = AcfCapabilityErrorCodes.CONFIRMATION_ERROR;
    public static final String ERROR_CONFIRMATION_UNAVAILABLE = AcfCapabilityErrorCodes.CONFIRMATION_UNAVAILABLE;
    public static final String ERROR_CONFIRMATION_TOKEN_INVALID = AcfCapabilityErrorCodes.CONFIRM_TOKEN_INVALID;
    public static final String ERROR_IDEMPOTENCY_KEY_REQUIRED = AcfCapabilityErrorCodes.IDEMPOTENCY_KEY_REQUIRED;
    public static final String ERROR_IDEMPOTENCY_UNAVAILABLE = AcfCapabilityErrorCodes.IDEMPOTENCY_UNAVAILABLE;
    public static final String ERROR_IDEMPOTENCY = AcfCapabilityErrorCodes.IDEMPOTENCY_ERROR;
    public static final String ERROR_IDEMPOTENCY_CONFLICT = AcfCapabilityErrorCodes.IDEMPOTENCY_CONFLICT;
    public static final String ERROR_INVOKE = AcfCapabilityErrorCodes.INVOKE_ERROR;

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityGovernanceService governanceService;
    private final CapabilityConfirmationService confirmationService;
    private final CapabilityIdempotencyService idempotencyService;
    private final CapabilityRequestDigestGenerator requestDigestGenerator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, null, null, new CapabilityRequestDigestGenerator(objectMapper),
                objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, confirmationService, null, requestDigestGenerator,
                objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this.capabilityRegistry = capabilityRegistry;
        this.governanceService = governanceService;
        this.confirmationService = confirmationService;
        this.idempotencyService = idempotencyService;
        this.requestDigestGenerator = requestDigestGenerator;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public CapabilityResult invoke(CapabilityInvokeCommand command) {
        String name = command == null ? null : command.getName();
        if (!StringUtils.hasText(name)) {
            return CapabilityResult.failure(name, ERROR_BAD_REQUEST, "Capability name is required");
        }

        CapabilityRegistration registration;
        try {
            registration = capabilityRegistry.getRegistration(name);
        } catch (IllegalArgumentException exception) {
            return CapabilityResult.failure(name, ERROR_CAPABILITY_NOT_FOUND, exception.getMessage());
        }

        CapabilityContext context = command.getContext() == null ? CapabilityContext.empty() : command.getContext();
        CapabilityPolicyResult policyResult;
        try {
            policyResult = Objects.requireNonNull(governanceService.evaluateExecution(registration.definition(), context),
                    "Capability governance result must not be null");
        } catch (RuntimeException exception) {
            return CapabilityResult.failure(name, ERROR_POLICY, readableMessage(exception));
        }
        if (!policyResult.isAllowed()) {
            String errorCode = StringUtils.hasText(policyResult.getErrorCode())
                    ? policyResult.getErrorCode() : ERROR_POLICY_DENIED;
            return CapabilityResult.denied(name, errorCode, policyResult.getReason());
        }

        CapabilityDefinition effectiveDefinition = policyResult.getDefinition() == null
                ? registration.definition() : policyResult.getDefinition();
        Object argument;
        try {
            argument = convertArgument(effectiveDefinition, command.getArguments());
            validateArgument(argument);
        } catch (IllegalArgumentException exception) {
            return CapabilityResult.failure(name, ERROR_BAD_REQUEST, readableMessage(exception));
        }

        String idempotencyKey = command.getIdempotencyKey();
        String requestDigest;
        try {
            requestDigest = requiresRequestDigest(effectiveDefinition, idempotencyKey)
                    ? requestDigestGenerator.generate(effectiveDefinition.getName(), argument) : null;
        } catch (RuntimeException exception) {
            String errorCode = StringUtils.hasText(idempotencyKey) ? ERROR_IDEMPOTENCY : ERROR_CONFIRMATION;
            return CapabilityResult.failure(name, errorCode, readableMessage(exception));
        }

        CapabilityResult confirmationChallengeResult = createConfirmationChallengeIfNecessary(effectiveDefinition,
                context, command.getConfirmationToken(), idempotencyKey, requestDigest);
        if (confirmationChallengeResult != null) {
            return confirmationChallengeResult;
        }

        CapabilityIdempotencyCheck idempotencyCheck = acquireIdempotency(effectiveDefinition, context,
                idempotencyKey, requestDigest);
        if (idempotencyCheck != null && idempotencyCheck.getStatus() != CapabilityIdempotencyStatus.ACQUIRED) {
            return toIdempotencyResult(name, idempotencyCheck);
        }
        boolean idempotencyAcquired = idempotencyCheck != null;

        CapabilityResult confirmationResult = verifyConfirmation(effectiveDefinition, context,
                command.getConfirmationToken(), idempotencyKey, requestDigest);
        if (confirmationResult != null) {
            if (idempotencyAcquired) {
                CapabilityResult releaseFailure = releaseIdempotency(effectiveDefinition, context,
                        idempotencyKey, requestDigest);
                if (releaseFailure != null) {
                    return releaseFailure;
                }
            }
            return confirmationResult;
        }

        try {
            Object rawResult = invokeTarget(registration, argument);
            CapabilityResult result = normalizeResult(name, rawResult);
            return idempotencyAcquired
                    ? completeIdempotency(effectiveDefinition, context, idempotencyKey, requestDigest, result) : result;
        } catch (InvocationTargetException exception) {
            CapabilityResult result = CapabilityResult.failure(
                    name, ERROR_INVOKE, readableMessage(exception.getTargetException()));
            failIdempotency(effectiveDefinition, context, idempotencyKey, requestDigest, result, idempotencyAcquired);
            return result;
        } catch (ReflectiveOperationException exception) {
            CapabilityResult result = CapabilityResult.failure(name, ERROR_INVOKE, readableMessage(exception));
            failIdempotency(effectiveDefinition, context, idempotencyKey, requestDigest, result, idempotencyAcquired);
            return result;
        }
    }

    /**
     * 业务能力可直接返回标准结果，也可以返回普通业务对象。
     * 这里统一补齐实际调用的 capability name，避免协议适配层再次判断结果形态。
     */
    private CapabilityResult normalizeResult(String name, Object rawResult) {
        if (rawResult instanceof CapabilityResult capabilityResult) {
            return capabilityResult.withName(name);
        }
        return CapabilityResult.success(name, rawResult);
    }

    private Object convertArgument(CapabilityDefinition definition, Object arguments) {
        Type argumentType = definition.getArgumentType();
        if (argumentType == Void.class || argumentType == Void.TYPE) {
            if (arguments == null || arguments instanceof Map<?, ?> map && map.isEmpty()) {
                return null;
            }
            throw new IllegalArgumentException("Capability does not accept arguments: " + definition.getName());
        }
        Object source = arguments == null ? Map.of() : arguments;
        JavaType javaType = objectMapper.getTypeFactory().constructType(argumentType);
        return objectMapper.convertValue(source, javaType);
    }

    private boolean requiresRequestDigest(CapabilityDefinition definition, String idempotencyKey) {
        return definition.isConfirmationRequired() || StringUtils.hasText(idempotencyKey);
    }

    private CapabilityResult createConfirmationChallengeIfNecessary(CapabilityDefinition definition,
                                                                    CapabilityContext context,
                                                                    String confirmationToken,
                                                                    String idempotencyKey,
                                                                    String requestDigest) {
        if (!definition.isConfirmationRequired()) {
            return null;
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            return CapabilityResult.failure(definition.getName(), ERROR_IDEMPOTENCY_KEY_REQUIRED,
                    "Confirmed capability requires an idempotency key");
        }
        if (confirmationService == null) {
            return CapabilityResult.failure(definition.getName(), ERROR_CONFIRMATION_UNAVAILABLE,
                    "Capability confirmation service is not configured");
        }
        if (StringUtils.hasText(confirmationToken)) {
            return null;
        }
        try {
            CapabilityConfirmationChallenge challenge = Objects.requireNonNull(
                    confirmationService.createChallenge(definition, context, idempotencyKey, requestDigest),
                    "Capability confirmation challenge must not be null");
            return CapabilityResult.confirmationRequired(definition.getName(), challenge);
        } catch (RuntimeException exception) {
            return CapabilityResult.failure(definition.getName(), ERROR_CONFIRMATION, readableMessage(exception));
        }
    }

    private CapabilityResult verifyConfirmation(CapabilityDefinition definition, CapabilityContext context,
                                                String confirmationToken, String idempotencyKey,
                                                String requestDigest) {
        if (!definition.isConfirmationRequired()) {
            return null;
        }
        try {
            CapabilityConfirmationCheck check = Objects.requireNonNull(
                    confirmationService.verifyAndConsumeToken(
                            definition, context, confirmationToken, idempotencyKey, requestDigest),
                    "Capability confirmation check must not be null");
            if (check.isValid()) {
                return null;
            }
            String errorCode = StringUtils.hasText(check.getErrorCode())
                    ? check.getErrorCode() : ERROR_CONFIRMATION_TOKEN_INVALID;
            return CapabilityResult.failure(definition.getName(), errorCode, check.getReason());
        } catch (RuntimeException exception) {
            return CapabilityResult.failure(definition.getName(), ERROR_CONFIRMATION, readableMessage(exception));
        }
    }

    private CapabilityIdempotencyCheck acquireIdempotency(CapabilityDefinition definition, CapabilityContext context,
                                                          String idempotencyKey, String requestDigest) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        if (idempotencyService == null) {
            return CapabilityIdempotencyCheck.conflict(ERROR_IDEMPOTENCY_UNAVAILABLE,
                    "Capability idempotency service is not configured");
        }
        try {
            CapabilityIdempotencyCheck check = Objects.requireNonNull(
                    idempotencyService.acquire(definition, context, idempotencyKey, requestDigest),
                    "Capability idempotency check must not be null");
            if (check.getStatus() == null) {
                throw new IllegalStateException("Capability idempotency status must not be null");
            }
            return check;
        } catch (RuntimeException exception) {
            return CapabilityIdempotencyCheck.conflict(ERROR_IDEMPOTENCY, readableMessage(exception));
        }
    }

    private CapabilityResult toIdempotencyResult(String name, CapabilityIdempotencyCheck check) {
        if (check.getStatus() == CapabilityIdempotencyStatus.REPLAYED) {
            if (check.getReplayResult() == null) {
                return CapabilityResult.failure(name, ERROR_IDEMPOTENCY,
                        "Replayed idempotency result must not be null");
            }
            return check.getReplayResult().withName(name);
        }
        String errorCode = StringUtils.hasText(check.getErrorCode())
                ? check.getErrorCode() : ERROR_IDEMPOTENCY_CONFLICT;
        return CapabilityResult.failure(name, errorCode, check.getReason());
    }

    private CapabilityResult completeIdempotency(CapabilityDefinition definition, CapabilityContext context,
                                                 String idempotencyKey, String requestDigest,
                                                 CapabilityResult result) {
        try {
            idempotencyService.complete(definition, context, idempotencyKey, requestDigest, result);
            return result;
        } catch (RuntimeException exception) {
            return CapabilityResult.failure(definition.getName(), ERROR_IDEMPOTENCY,
                    "Capability executed but idempotency result could not be stored: " + readableMessage(exception));
        }
    }

    private CapabilityResult releaseIdempotency(CapabilityDefinition definition, CapabilityContext context,
                                                String idempotencyKey, String requestDigest) {
        try {
            idempotencyService.release(definition, context, idempotencyKey, requestDigest);
            return null;
        } catch (RuntimeException exception) {
            return CapabilityResult.failure(definition.getName(), ERROR_IDEMPOTENCY,
                    "Failed to release idempotency execution right: " + readableMessage(exception));
        }
    }

    private void failIdempotency(CapabilityDefinition definition, CapabilityContext context,
                                 String idempotencyKey, String requestDigest,
                                 CapabilityResult result, boolean idempotencyAcquired) {
        if (!idempotencyAcquired) {
            return;
        }
        try {
            idempotencyService.fail(definition, context, idempotencyKey, requestDigest, result);
        } catch (RuntimeException ignored) {
            // 目标执行错误优先返回；幂等收口异常后续由审计与运行指标记录。
        }
    }

    private void validateArgument(Object argument) {
        if (argument == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(argument);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<Object> violation = violations.stream()
                .min(Comparator.comparing(item -> item.getPropertyPath() + " " + item.getMessage()))
                .orElseThrow();
        throw new IllegalArgumentException(violation.getPropertyPath() + " " + violation.getMessage());
    }

    private Object invokeTarget(CapabilityRegistration registration, Object argument)
            throws InvocationTargetException, IllegalAccessException {
        if (registration.method().getParameterCount() == 0) {
            return registration.method().invoke(registration.bean());
        }
        return registration.method().invoke(registration.bean(), argument);
    }

    private String readableMessage(Throwable throwable) {
        return StringUtils.hasText(throwable.getMessage())
                ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

}
