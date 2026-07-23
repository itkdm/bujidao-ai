package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConfirmationStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyAuditStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityPolicyResult;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityExceptionClassification;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationExecutor;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardChain;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardContext;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimePolicy;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimePolicyService;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityInvocationExecutor;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityRuntimePolicyService;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ACF 基础能力执行器
 *
 * 负责执行调用前治理，把外部参数转换为能力方法声明的 Java 类型，
 * 完成 Bean Validation 校验并调用已注册方法。
 *
 * @author bujidao
 */
public class CapabilityExecutor {

    private static final CapabilityExceptionClassifier FALLBACK_EXCEPTION_CLASSIFIER =
            new DefaultCapabilityExceptionClassifier();
    private static final CapabilityRuntimePolicyService DEFAULT_RUNTIME_POLICY_SERVICE =
            new DefaultCapabilityRuntimePolicyService();
    private static final CapabilityRuntimeGuardChain EMPTY_RUNTIME_GUARD_CHAIN =
            new CapabilityRuntimeGuardChain(List.of());
    private static final CapabilityInvocationExecutor DEFAULT_INVOCATION_EXECUTOR =
            DefaultCapabilityInvocationExecutor.shared();

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
    public static final String ERROR_RUNTIME_POLICY = AcfCapabilityErrorCodes.RUNTIME_POLICY_ERROR;
    public static final String ERROR_INVOKE = AcfCapabilityErrorCodes.INVOKE_ERROR;

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityGovernanceService governanceService;
    private final CapabilityConfirmationService confirmationService;
    private final CapabilityIdempotencyService idempotencyService;
    private final CapabilityAuditService auditService;
    private final CapabilityExceptionClassifier exceptionClassifier;
    private final CapabilityRuntimePolicyService runtimePolicyService;
    private final CapabilityRuntimeGuardChain runtimeGuardChain;
    private final CapabilityInvocationExecutor invocationExecutor;
    private final CapabilityRequestDigestGenerator requestDigestGenerator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, null, null, null,
                new DefaultCapabilityExceptionClassifier(), DEFAULT_RUNTIME_POLICY_SERVICE, EMPTY_RUNTIME_GUARD_CHAIN,
                DEFAULT_INVOCATION_EXECUTOR, new CapabilityRequestDigestGenerator(objectMapper),
                objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, confirmationService, null, null,
                new DefaultCapabilityExceptionClassifier(), DEFAULT_RUNTIME_POLICY_SERVICE, EMPTY_RUNTIME_GUARD_CHAIN,
                DEFAULT_INVOCATION_EXECUTOR, requestDigestGenerator, objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, confirmationService, idempotencyService, null,
                new DefaultCapabilityExceptionClassifier(), DEFAULT_RUNTIME_POLICY_SERVICE, EMPTY_RUNTIME_GUARD_CHAIN,
                DEFAULT_INVOCATION_EXECUTOR, requestDigestGenerator, objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityAuditService auditService,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, confirmationService, idempotencyService, auditService,
                new DefaultCapabilityExceptionClassifier(), DEFAULT_RUNTIME_POLICY_SERVICE, EMPTY_RUNTIME_GUARD_CHAIN,
                DEFAULT_INVOCATION_EXECUTOR, requestDigestGenerator, objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityAuditService auditService,
                              CapabilityExceptionClassifier exceptionClassifier,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, confirmationService, idempotencyService, auditService,
                exceptionClassifier, DEFAULT_RUNTIME_POLICY_SERVICE, EMPTY_RUNTIME_GUARD_CHAIN,
                DEFAULT_INVOCATION_EXECUTOR, requestDigestGenerator, objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityAuditService auditService,
                              CapabilityExceptionClassifier exceptionClassifier,
                              CapabilityRuntimePolicyService runtimePolicyService,
                              CapabilityRuntimeGuardChain runtimeGuardChain,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this(capabilityRegistry, governanceService, confirmationService, idempotencyService, auditService,
                exceptionClassifier, runtimePolicyService, runtimeGuardChain, DEFAULT_INVOCATION_EXECUTOR,
                requestDigestGenerator, objectMapper, validator);
    }

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityAuditService auditService,
                              CapabilityExceptionClassifier exceptionClassifier,
                              CapabilityRuntimePolicyService runtimePolicyService,
                              CapabilityRuntimeGuardChain runtimeGuardChain,
                              CapabilityInvocationExecutor invocationExecutor,
                              CapabilityRequestDigestGenerator requestDigestGenerator,
                              ObjectMapper objectMapper, Validator validator) {
        this.capabilityRegistry = capabilityRegistry;
        this.governanceService = governanceService;
        this.confirmationService = confirmationService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.exceptionClassifier = Objects.requireNonNull(exceptionClassifier, "exceptionClassifier");
        this.runtimePolicyService = Objects.requireNonNull(runtimePolicyService, "runtimePolicyService");
        this.runtimeGuardChain = Objects.requireNonNull(runtimeGuardChain, "runtimeGuardChain");
        this.invocationExecutor = Objects.requireNonNull(invocationExecutor, "invocationExecutor");
        this.requestDigestGenerator = requestDigestGenerator;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public CapabilityResult invoke(CapabilityInvokeCommand command) {
        long startedAt = System.currentTimeMillis();
        String name = command == null ? null : command.getName();
        CapabilityContext suppliedContext = command == null || command.getContext() == null
                ? CapabilityContext.empty() : command.getContext();
        String traceId = StringUtils.hasText(suppliedContext.getTraceId())
                ? suppliedContext.getTraceId() : "acf-" + UUID.randomUUID();
        CapabilityContext context = suppliedContext.withTraceId(traceId);
        CapabilityExecutionAudit audit = new CapabilityExecutionAudit(
                auditService, traceId, name, context, startedAt);

        CapabilityResult result;
        try {
            result = doInvoke(command, name, context, audit);
        } catch (RuntimeException exception) {
            result = classifyException(name, exception);
            audit.failure(CapabilityAuditStage.INVOCATION, result, startedAt);
        }
        result = result.withTraceId(traceId);
        audit.finish(result);
        return result;
    }

    private CapabilityResult doInvoke(CapabilityInvokeCommand command, String name, CapabilityContext context,
                                      CapabilityExecutionAudit audit) {
        long stepStartedAt = System.currentTimeMillis();
        if (!StringUtils.hasText(name)) {
            CapabilityResult result = CapabilityResult.failure(name, ERROR_BAD_REQUEST, "Capability name is required");
            audit.failure(CapabilityAuditStage.REQUEST_VALIDATION, result, stepStartedAt);
            return result;
        }
        audit.success(CapabilityAuditStage.REQUEST_VALIDATION, "capability name is valid", stepStartedAt);

        CapabilityRegistration registration;
        stepStartedAt = System.currentTimeMillis();
        try {
            registration = capabilityRegistry.getRegistration(name);
        } catch (IllegalArgumentException exception) {
            CapabilityResult result = CapabilityResult.failure(
                    name, ERROR_CAPABILITY_NOT_FOUND, exception.getMessage());
            audit.failure(CapabilityAuditStage.CAPABILITY_LOOKUP, result, stepStartedAt);
            return result;
        }
        audit.definition(registration.definition());
        audit.success(CapabilityAuditStage.CAPABILITY_LOOKUP, "capability registered", stepStartedAt);

        CapabilityPolicyResult policyResult;
        stepStartedAt = System.currentTimeMillis();
        try {
            policyResult = Objects.requireNonNull(
                    governanceService.evaluateExecution(registration.definition(), context),
                    "Capability governance result must not be null");
        } catch (RuntimeException exception) {
            CapabilityResult result = CapabilityResult.failure(name, ERROR_POLICY, readableMessage(exception));
            audit.failure(CapabilityAuditStage.GOVERNANCE, result, stepStartedAt);
            return result;
        }
        if (!policyResult.isAllowed()) {
            String errorCode = StringUtils.hasText(policyResult.getErrorCode())
                    ? policyResult.getErrorCode() : ERROR_POLICY_DENIED;
            CapabilityResult result = CapabilityResult.denied(name, errorCode, policyResult.getReason());
            audit.failure(CapabilityAuditStage.GOVERNANCE, result, stepStartedAt);
            return result;
        }

        CapabilityDefinition effectiveDefinition = policyResult.getDefinition() == null
                ? registration.definition() : policyResult.getDefinition();
        audit.definition(effectiveDefinition);
        audit.success(CapabilityAuditStage.GOVERNANCE, "execution allowed", stepStartedAt);
        Object argument;
        stepStartedAt = System.currentTimeMillis();
        try {
            argument = convertArgument(effectiveDefinition, command.getArguments());
            validateArgument(argument);
        } catch (IllegalArgumentException exception) {
            CapabilityResult result = CapabilityResult.failure(name, ERROR_BAD_REQUEST, readableMessage(exception));
            audit.failure(CapabilityAuditStage.ARGUMENT_VALIDATION, result, stepStartedAt);
            return result;
        }
        audit.success(CapabilityAuditStage.ARGUMENT_VALIDATION, "arguments converted and validated", stepStartedAt);

        String idempotencyKey = command.getIdempotencyKey();
        String requestDigest;
        stepStartedAt = System.currentTimeMillis();
        try {
            requestDigest = requiresRequestDigest(effectiveDefinition, idempotencyKey)
                    ? requestDigestGenerator.generate(effectiveDefinition.getName(), argument) : null;
        } catch (RuntimeException exception) {
            String errorCode = StringUtils.hasText(idempotencyKey) ? ERROR_IDEMPOTENCY : ERROR_CONFIRMATION;
            CapabilityResult result = CapabilityResult.failure(name, errorCode, readableMessage(exception));
            CapabilityAuditStage stage = StringUtils.hasText(idempotencyKey)
                    ? CapabilityAuditStage.IDEMPOTENCY : CapabilityAuditStage.CONFIRMATION;
            audit.failure(stage, result, stepStartedAt);
            return result;
        }

        stepStartedAt = System.currentTimeMillis();
        CapabilityResult confirmationChallengeResult = createConfirmationChallengeIfNecessary(effectiveDefinition,
                context, command.getConfirmationToken(), idempotencyKey, requestDigest);
        if (confirmationChallengeResult != null) {
            if (confirmationChallengeResult.getStatus() == CapabilityStatus.CONFIRM_REQUIRED) {
                audit.confirmationStatus(CapabilityConfirmationStatus.CHALLENGE_CREATED);
                audit.success(CapabilityAuditStage.CONFIRMATION, "confirmation challenge created", stepStartedAt);
            } else {
                audit.confirmationStatus(CapabilityConfirmationStatus.ERROR);
                audit.failure(CapabilityAuditStage.CONFIRMATION, confirmationChallengeResult, stepStartedAt);
            }
            return confirmationChallengeResult;
        }
        if (!effectiveDefinition.isConfirmationRequired()) {
            audit.confirmationStatus(CapabilityConfirmationStatus.NOT_REQUIRED);
            audit.success(CapabilityAuditStage.CONFIRMATION, "confirmation not required", stepStartedAt);
        }

        stepStartedAt = System.currentTimeMillis();
        CapabilityIdempotencyCheck idempotencyCheck = acquireIdempotency(effectiveDefinition, context,
                idempotencyKey, requestDigest);
        if (idempotencyCheck != null && idempotencyCheck.getStatus() != CapabilityIdempotencyStatus.ACQUIRED) {
            CapabilityResult idempotencyResult = toIdempotencyResult(name, idempotencyCheck);
            if (idempotencyCheck.getStatus() == CapabilityIdempotencyStatus.REPLAYED) {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.REPLAYED);
                audit.success(CapabilityAuditStage.IDEMPOTENCY, "stored result replayed", stepStartedAt);
            } else if (idempotencyCheck.getStatus() == CapabilityIdempotencyStatus.ERROR) {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.ERROR);
                audit.failure(CapabilityAuditStage.IDEMPOTENCY, idempotencyResult, stepStartedAt);
            } else {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.CONFLICT);
                audit.failure(CapabilityAuditStage.IDEMPOTENCY, idempotencyResult, stepStartedAt);
            }
            return idempotencyResult;
        }
        boolean idempotencyAcquired = idempotencyCheck != null;
        if (idempotencyAcquired) {
            audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.ACQUIRED);
            audit.success(CapabilityAuditStage.IDEMPOTENCY, "execution right acquired", stepStartedAt);
        } else {
            audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.NOT_REQUESTED);
            audit.success(CapabilityAuditStage.IDEMPOTENCY, "idempotency not requested", stepStartedAt);
        }

        stepStartedAt = System.currentTimeMillis();
        CapabilityResult confirmationResult = verifyConfirmation(effectiveDefinition, context,
                command.getConfirmationToken(), idempotencyKey, requestDigest);
        if (confirmationResult != null) {
            audit.confirmationStatus(ERROR_CONFIRMATION.equals(confirmationResult.getErrorCode())
                    ? CapabilityConfirmationStatus.ERROR
                    : CapabilityConfirmationStatus.TOKEN_INVALID);
            audit.failure(CapabilityAuditStage.CONFIRMATION, confirmationResult, stepStartedAt);
            return finishBeforeTarget(effectiveDefinition, context, idempotencyKey, requestDigest,
                    confirmationResult, idempotencyAcquired, audit);
        }
        if (effectiveDefinition.isConfirmationRequired()) {
            audit.confirmationStatus(CapabilityConfirmationStatus.TOKEN_VALID);
            audit.success(CapabilityAuditStage.CONFIRMATION, "confirmation token accepted", stepStartedAt);
        }

        CapabilityRuntimePolicy runtimePolicy;
        stepStartedAt = System.currentTimeMillis();
        try {
            runtimePolicy = Objects.requireNonNull(runtimePolicyService.resolve(effectiveDefinition, context),
                    "Capability runtime policy must not be null");
            audit.runtimePolicy(runtimePolicy);
            audit.success(CapabilityAuditStage.RUNTIME_POLICY, runtimePolicy.summary(), stepStartedAt);
        } catch (RuntimeException exception) {
            CapabilityResult result = CapabilityResult.failure(name, ERROR_RUNTIME_POLICY,
                    readableMessage(exception));
            audit.failure(CapabilityAuditStage.RUNTIME_POLICY, result, stepStartedAt);
            return finishBeforeTarget(effectiveDefinition, context, idempotencyKey, requestDigest,
                    result, idempotencyAcquired, audit);
        }

        CapabilityRuntimeGuardChain.Lease guardLease;
        stepStartedAt = System.currentTimeMillis();
        try {
            CapabilityRuntimeGuardContext guardContext = new CapabilityRuntimeGuardContext(
                    effectiveDefinition, context, runtimePolicy);
            guardLease = runtimeGuardChain.acquire(guardContext);
        } catch (RuntimeException exception) {
            CapabilityResult result = CapabilityResult.failure(name,
                    AcfCapabilityErrorCodes.RUNTIME_GUARD_ERROR, readableMessage(exception));
            audit.failure(CapabilityAuditStage.RUNTIME_GUARD, result, stepStartedAt);
            return finishBeforeTarget(effectiveDefinition, context, idempotencyKey, requestDigest,
                    result, idempotencyAcquired, audit);
        }
        if (!guardLease.isAllowed()) {
            CapabilityRuntimeGuardResult rejection = guardLease.getRejection();
            audit.runtimeGuardCode(rejection.getGuardCode());
            CapabilityResult result = CapabilityResult.failure(name, rejection.getErrorCode(),
                    rejection.getReason(), rejection.isRetryable());
            audit.failure(CapabilityAuditStage.RUNTIME_GUARD, result, stepStartedAt);
            return finishBeforeTarget(effectiveDefinition, context, idempotencyKey, requestDigest,
                    result, idempotencyAcquired, audit);
        }
        audit.success(CapabilityAuditStage.RUNTIME_GUARD, "runtime guards acquired", stepStartedAt);

        stepStartedAt = System.currentTimeMillis();
        audit.targetInvoked();
        try {
            CapabilityResult result = invocationExecutor.invoke(
                    () -> normalizeResult(name, invokeTarget(registration, argument)), runtimePolicy.getTimeoutMs());
            if (result.isSuccess()) {
                guardLease.onSuccess(result);
                audit.success(CapabilityAuditStage.INVOCATION, "target invocation completed", stepStartedAt);
            } else {
                guardLease.onFailure(result, null);
                audit.failure(CapabilityAuditStage.INVOCATION, result, stepStartedAt);
            }
            if (!idempotencyAcquired) {
                return result;
            }
            long completionStartedAt = System.currentTimeMillis();
            CapabilityResult completedResult = completeIdempotency(
                    effectiveDefinition, context, idempotencyKey, requestDigest, result);
            if (completedResult == result) {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.COMPLETED);
                audit.success(CapabilityAuditStage.IDEMPOTENCY, "result stored", completionStartedAt);
            } else {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.ERROR);
                audit.failure(CapabilityAuditStage.IDEMPOTENCY, completedResult, completionStartedAt);
            }
            return completedResult;
        } catch (Exception exception) {
            CapabilityResult result = classifyException(name, exception);
            guardLease.onFailure(result, exception);
            audit.failure(CapabilityAuditStage.INVOCATION, result, stepStartedAt);
            failIdempotency(effectiveDefinition, context, idempotencyKey, requestDigest, result, idempotencyAcquired);
            if (idempotencyAcquired) {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.FAILED);
            }
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

    private CapabilityResult classifyException(String name, Throwable throwable) {
        try {
            CapabilityExceptionClassification classification = Objects.requireNonNull(
                    exceptionClassifier.classify(throwable), "Capability exception classification must not be null");
            return CapabilityResult.failure(name, classification.getErrorCode(), classification.getMessage(),
                    classification.isRetryable());
        } catch (RuntimeException classificationException) {
            // 自定义分类器不能掩盖原始执行异常，分类失败时回退到最保守的不可重试结果。
            CapabilityExceptionClassification fallback = FALLBACK_EXCEPTION_CLASSIFIER.classify(throwable);
            return CapabilityResult.failure(name, fallback.getErrorCode(), fallback.getMessage(),
                    fallback.isRetryable());
        }
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
            return CapabilityIdempotencyCheck.error(ERROR_IDEMPOTENCY_UNAVAILABLE,
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
            return CapabilityIdempotencyCheck.error(ERROR_IDEMPOTENCY, readableMessage(exception));
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

    /**
     * 目标方法尚未执行时，已获取的幂等执行权必须释放而不是标记失败。
     * 这样确认失败、运行策略异常或保护器拒绝都不会永久占用同一个幂等键。
     */
    private CapabilityResult finishBeforeTarget(CapabilityDefinition definition, CapabilityContext context,
                                                String idempotencyKey, String requestDigest,
                                                CapabilityResult result, boolean idempotencyAcquired,
                                                CapabilityExecutionAudit audit) {
        if (!idempotencyAcquired) {
            return result;
        }
        long releaseStartedAt = System.currentTimeMillis();
        CapabilityResult releaseFailure = releaseIdempotency(definition, context, idempotencyKey, requestDigest);
        if (releaseFailure != null) {
            audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.ERROR);
            audit.failure(CapabilityAuditStage.IDEMPOTENCY, releaseFailure, releaseStartedAt);
            return releaseFailure;
        }
        audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.RELEASED);
        audit.success(CapabilityAuditStage.IDEMPOTENCY, "execution right released", releaseStartedAt);
        return result;
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
