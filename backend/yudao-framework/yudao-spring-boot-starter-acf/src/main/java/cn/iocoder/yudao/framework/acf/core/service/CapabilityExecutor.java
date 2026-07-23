package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
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
    public static final String ERROR_INVOKE = AcfCapabilityErrorCodes.INVOKE_ERROR;

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityGovernanceService governanceService;
    private final CapabilityConfirmationService confirmationService;
    private final CapabilityRequestDigestGenerator requestDigestGenerator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, null, new CapabilityRequestDigestGenerator(objectMapper),
                objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this.capabilityRegistry = capabilityRegistry;
        this.governanceService = governanceService;
        this.confirmationService = confirmationService;
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
        try {
            Object argument = convertArgument(effectiveDefinition, command.getArguments());
            validateArgument(argument);
            CapabilityResult confirmationResult = evaluateConfirmation(effectiveDefinition, context,
                    command.getConfirmationToken(), argument);
            if (confirmationResult != null) {
                return confirmationResult;
            }
            Object rawResult = invokeTarget(registration, argument);
            return normalizeResult(name, rawResult);
        } catch (IllegalArgumentException exception) {
            return CapabilityResult.failure(name, ERROR_BAD_REQUEST, readableMessage(exception));
        } catch (InvocationTargetException exception) {
            return CapabilityResult.failure(name, ERROR_INVOKE, readableMessage(exception.getTargetException()));
        } catch (ReflectiveOperationException exception) {
            return CapabilityResult.failure(name, ERROR_INVOKE, readableMessage(exception));
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

    private CapabilityResult evaluateConfirmation(CapabilityDefinition definition, CapabilityContext context,
                                                  String confirmationToken, Object argument) {
        if (!definition.isConfirmationRequired()) {
            return null;
        }
        if (confirmationService == null) {
            return CapabilityResult.failure(definition.getName(), ERROR_CONFIRMATION_UNAVAILABLE,
                    "Capability confirmation service is not configured");
        }
        try {
            String requestDigest = requestDigestGenerator.generate(definition.getName(), argument);
            if (!StringUtils.hasText(confirmationToken)) {
                CapabilityConfirmationChallenge challenge = Objects.requireNonNull(
                        confirmationService.createChallenge(definition, context, requestDigest),
                        "Capability confirmation challenge must not be null");
                return CapabilityResult.confirmationRequired(definition.getName(), challenge);
            }
            CapabilityConfirmationCheck check = Objects.requireNonNull(
                    confirmationService.verifyAndConsumeToken(definition, context, confirmationToken, requestDigest),
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
