package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConfirmationStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyAuditStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
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
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationCompletion;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationExecutor;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationHandle;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationInterruptResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardChain;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardContext;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeMetricRecord;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeMetricsRecorder;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimePolicy;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimePolicyService;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * ACF 基础能力执行器
 *
 * 负责执行调用前治理，把外部参数转换为能力方法声明的 Java 类型，
 * 完成 Bean Validation 校验并调用已注册方法。
 *
 * @author bujidao
 */
public class CapabilityExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CapabilityExecutor.class);

    private static final CapabilityExceptionClassifier FALLBACK_EXCEPTION_CLASSIFIER =
            new DefaultCapabilityExceptionClassifier();
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
    public static final String ERROR_RUNTIME_INTERRUPTED = AcfCapabilityErrorCodes.RUNTIME_INTERRUPTED;
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
    private final CapabilityRuntimeMetricsRecorder metricsRecorder;
    private final CapabilityRequestDigestGenerator requestDigestGenerator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapabilityExecutor(CapabilityRegistry capabilityRegistry, CapabilityGovernanceService governanceService,
                              CapabilityConfirmationService confirmationService,
                              CapabilityIdempotencyService idempotencyService,
                              CapabilityAuditService auditService,
                              CapabilityExceptionClassifier exceptionClassifier,
                              CapabilityRuntimePolicyService runtimePolicyService,
                              CapabilityRuntimeGuardChain runtimeGuardChain,
                              CapabilityInvocationExecutor invocationExecutor,
                              CapabilityRuntimeMetricsRecorder metricsRecorder,
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
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder");
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
        recordMetrics(audit.finish(result));
        return result;
    }

    private void recordMetrics(CapabilityAuditRecord auditRecord) {
        CapabilityRuntimeMetricRecord metricRecord = CapabilityRuntimeMetricRecord.builder()
                .capabilityName(auditRecord.getCapabilityName())
                .capabilityVersion(auditRecord.getCapabilityVersion())
                .finalStage(auditRecord.getFinalStage())
                .status(auditRecord.getStatus())
                .errorCode(auditRecord.getErrorCode())
                .runtimeGuardCode(auditRecord.getRuntimeGuardCode())
                .retryCount(auditRecord.getRetryCount())
                .targetInvoked(auditRecord.isTargetInvoked())
                .retryable(auditRecord.isRetryable())
                .latencyMs(auditRecord.getLatencyMs())
                .build();
        try {
            metricsRecorder.record(metricRecord);
        } catch (RuntimeException exception) {
            // 指标属于旁路观测能力，记录失败不能覆盖已经确定的业务调用结果。
            LOGGER.warn("ACF metrics recording failed, capability={}, errorType={}",
                    auditRecord.getCapabilityName(), exception.getClass().getName());
        }
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
                    name, ERROR_CAPABILITY_NOT_FOUND, "Capability not found");
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
            CapabilityResult result = CapabilityResult.failure(name, ERROR_POLICY,
                    "Capability policy evaluation failed");
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
            CapabilityResult result = CapabilityResult.failure(name, ERROR_BAD_REQUEST,
                    "Capability request is invalid");
            audit.failure(CapabilityAuditStage.ARGUMENT_VALIDATION, result, stepStartedAt);
            return result;
        }
        audit.success(CapabilityAuditStage.ARGUMENT_VALIDATION, "arguments converted and validated", stepStartedAt);

        String idempotencyKey = command.getIdempotencyKey();
        if (requiresIdempotency(effectiveDefinition) && !StringUtils.hasText(idempotencyKey)) {
            CapabilityResult result = CapabilityResult.failure(name, ERROR_IDEMPOTENCY_KEY_REQUIRED,
                    "Side-effecting or confirmed capability requires an idempotency key");
            audit.failure(CapabilityAuditStage.IDEMPOTENCY, result, System.currentTimeMillis());
            return result;
        }
        String requestDigest;
        stepStartedAt = System.currentTimeMillis();
        try {
            requestDigest = requiresRequestDigest(effectiveDefinition, idempotencyKey)
                    ? requestDigestGenerator.generate(effectiveDefinition.getName(), argument) : null;
        } catch (RuntimeException exception) {
            String errorCode = StringUtils.hasText(idempotencyKey) ? ERROR_IDEMPOTENCY : ERROR_CONFIRMATION;
            CapabilityResult result = CapabilityResult.failure(name, errorCode,
                    "Capability request digest generation failed");
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
                    "Capability runtime policy resolution failed");
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
                    AcfCapabilityErrorCodes.RUNTIME_GUARD_ERROR, "Capability runtime guard failed");
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
        CapabilityInvocationOutcome invocationOutcome = invokeWithRetry(
                name, registration, argument, effectiveDefinition, runtimePolicy, audit);
        CapabilityResult result = invocationOutcome.result();
        if (invocationOutcome.deferredHandle() != null) {
            audit.failure(CapabilityAuditStage.INVOCATION, result, stepStartedAt);
            markIdempotencyUncertain(effectiveDefinition, context, idempotencyKey, requestDigest,
                    result, idempotencyAcquired, audit);
            settleDeferredInvocation(effectiveDefinition, context, idempotencyKey, requestDigest,
                    result, invocationOutcome, idempotencyAcquired, guardLease);
            return result;
        }
        if (!invocationOutcome.targetInvoked()) {
            guardLease.release();
            audit.failure(CapabilityAuditStage.INVOCATION, result, stepStartedAt);
            return finishBeforeTarget(effectiveDefinition, context, idempotencyKey, requestDigest,
                    result, idempotencyAcquired, audit);
        }
        if (result.isSuccess()) {
            guardLease.onSuccess(result);
            audit.success(CapabilityAuditStage.INVOCATION, "target invocation completed", stepStartedAt);
        } else {
            guardLease.onFailure(result, invocationOutcome.failureCause());
            audit.failure(CapabilityAuditStage.INVOCATION, result, stepStartedAt);
        }
        if (invocationOutcome.failureCause() != null) {
            failIdempotency(effectiveDefinition, context, idempotencyKey, requestDigest, result, idempotencyAcquired);
            if (idempotencyAcquired) {
                audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.FAILED);
            }
            return result;
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
    }

    private CapabilityInvocationOutcome invokeWithRetry(String name, CapabilityRegistration registration,
                                                        Object argument, CapabilityDefinition definition,
                                                        CapabilityRuntimePolicy runtimePolicy,
                                                        CapabilityExecutionAudit audit) {
        int maxAttempts = runtimePolicy.isRetryEnabled() ? runtimePolicy.getRetryMaxAttempts() : 1;
        CapabilityInvocationOutcome outcome = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            outcome = invokeOnce(name, registration, argument, runtimePolicy.getTimeoutMs());
            if (outcome.targetInvoked()) {
                audit.targetInvoked();
            }
            if (!shouldRetry(definition, runtimePolicy, outcome, attempt, maxAttempts)) {
                return outcome;
            }
            long retryStartedAt = System.currentTimeMillis();
            try {
                sleepBeforeRetry(runtimePolicy.getRetryBackoffMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new CapabilityInvocationOutcome(
                        CapabilityResult.failure(name, ERROR_RUNTIME_INTERRUPTED,
                                "Capability invocation was interrupted"),
                        exception, outcome.targetInvoked(), null);
            }
            audit.retry(attempt + 1, outcome.result().getErrorCode(), retryStartedAt);
        }
        return Objects.requireNonNull(outcome, "Capability invocation outcome must not be null");
    }

    private CapabilityInvocationOutcome invokeOnce(String name, CapabilityRegistration registration,
                                                   Object argument, int timeoutMs) {
        CapabilityInvocationHandle handle;
        try {
            handle = invocationExecutor.submit(
                    () -> normalizeResult(name, invokeTarget(registration, argument)));
        } catch (RuntimeException exception) {
            return new CapabilityInvocationOutcome(
                    classifyException(name, exception), exception, false, null);
        }
        try {
            return new CapabilityInvocationOutcome(handle.await(timeoutMs), null, true, null);
        } catch (TimeoutException exception) {
            TimeoutException timeoutException = new TimeoutException(
                    "Capability invocation timed out after " + timeoutMs + " ms");
            timeoutException.initCause(exception);
            return interruptedInvocationOutcome(name, handle, timeoutException,
                    AcfCapabilityErrorCodes.RUNTIME_TIMEOUT, "Capability invocation timed out", true);
        } catch (InterruptedException exception) {
            CapabilityInvocationOutcome outcome = interruptedInvocationOutcome(name, handle, exception,
                    AcfCapabilityErrorCodes.RUNTIME_INTERRUPTED, "Capability invocation was interrupted", false);
            Thread.currentThread().interrupt();
            return outcome;
        } catch (ExecutionException exception) {
            return new CapabilityInvocationOutcome(
                    classifyException(name, exception), exception, true, null);
        }
    }

    private CapabilityInvocationOutcome interruptedInvocationOutcome(
            String name, CapabilityInvocationHandle handle, Throwable failure,
            String errorCode, String message, boolean retryable) {
        CapabilityInvocationInterruptResult interruptResult = handle.interrupt();
        CapabilityResult result = CapabilityResult.failure(name, errorCode, message, retryable);
        if (interruptResult == CapabilityInvocationInterruptResult.CANCELLED_BEFORE_START) {
            return new CapabilityInvocationOutcome(result, failure, false, null);
        }
        return new CapabilityInvocationOutcome(result, failure, true, handle);
    }

    private boolean shouldRetry(CapabilityDefinition definition, CapabilityRuntimePolicy runtimePolicy,
                                CapabilityInvocationOutcome outcome, int attempt, int maxAttempts) {
        if (!runtimePolicy.isRetryEnabled() || attempt >= maxAttempts || !outcome.result().isRetryable()) {
            return false;
        }
        // 超时只说明调用方停止等待，不能证明工作线程已经结束，继续重试可能形成重叠执行。
        if (AcfCapabilityErrorCodes.RUNTIME_TIMEOUT.equals(outcome.result().getErrorCode())) {
            return false;
        }
        if (AcfCapabilityErrorCodes.RUNTIME_INTERRUPTED.equals(outcome.result().getErrorCode())) {
            return false;
        }
        if (!definition.isSideEffect()) {
            return true;
        }
        // 副作用能力只允许重试明确发生在目标方法启动前的线程池拒绝。
        return !outcome.targetInvoked()
                && AcfCapabilityErrorCodes.RUNTIME_EXECUTOR_REJECTED.equals(outcome.result().getErrorCode());
    }

    private void sleepBeforeRetry(int backoffMs) throws InterruptedException {
        if (backoffMs > 0) {
            Thread.sleep(backoffMs);
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
            return CapabilityResult.failure(name, classification.getErrorCode(), classification.getPublicMessage(),
                    classification.isRetryable());
        } catch (RuntimeException classificationException) {
            // 自定义分类器不能掩盖原始执行异常，分类失败时回退到最保守的不可重试结果。
            CapabilityExceptionClassification fallback = FALLBACK_EXCEPTION_CLASSIFIER.classify(throwable);
            return CapabilityResult.failure(name, fallback.getErrorCode(), fallback.getPublicMessage(),
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
        return requiresIdempotency(definition) || StringUtils.hasText(idempotencyKey);
    }

    private boolean requiresIdempotency(CapabilityDefinition definition) {
        return definition.isSideEffect() || definition.isConfirmationRequired();
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
            return CapabilityResult.failure(definition.getName(), ERROR_CONFIRMATION,
                    "Capability confirmation challenge creation failed");
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
            return CapabilityResult.failure(definition.getName(), ERROR_CONFIRMATION,
                    "Capability confirmation verification failed");
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
            return CapabilityIdempotencyCheck.error(ERROR_IDEMPOTENCY,
                    "Capability idempotency acquisition failed");
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
                    "Capability executed but idempotency result could not be stored");
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
                    "Failed to release idempotency execution right");
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

    private void markIdempotencyUncertain(CapabilityDefinition definition, CapabilityContext context,
                                          String idempotencyKey, String requestDigest,
                                          CapabilityResult result, boolean idempotencyAcquired,
                                          CapabilityExecutionAudit audit) {
        if (!idempotencyAcquired) {
            return;
        }
        try {
            idempotencyService.markUncertain(definition, context, idempotencyKey, requestDigest, result);
            audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.UNCERTAIN);
        } catch (RuntimeException exception) {
            audit.idempotencyStatus(CapabilityIdempotencyAuditStatus.ERROR);
            LOGGER.warn("Failed to mark ACF idempotency state uncertain, traceId={}, capability={}, errorType={}",
                    context.getTraceId(), definition.getName(), exception.getClass().getName());
        }
    }

    private void settleDeferredInvocation(CapabilityDefinition definition, CapabilityContext context,
                                          String idempotencyKey, String requestDigest,
                                          CapabilityResult callerResult, CapabilityInvocationOutcome outcome,
                                          boolean idempotencyAcquired,
                                          CapabilityRuntimeGuardChain.Lease guardLease) {
        outcome.deferredHandle().completion().whenComplete((completion, completionFailure) -> {
            try {
                settleDeferredIdempotency(definition, context, idempotencyKey, requestDigest,
                        idempotencyAcquired, completion, completionFailure);
            } finally {
                if (completion != null && !completion.isTargetInvoked()) {
                    guardLease.release();
                } else {
                    guardLease.onFailure(callerResult, outcome.failureCause());
                }
            }
        });
    }

    private void settleDeferredIdempotency(CapabilityDefinition definition, CapabilityContext context,
                                           String idempotencyKey, String requestDigest,
                                           boolean idempotencyAcquired,
                                           CapabilityInvocationCompletion completion,
                                           Throwable completionFailure) {
        if (!idempotencyAcquired) {
            return;
        }
        try {
            if (completionFailure != null) {
                CapabilityResult failure = CapabilityResult.failure(definition.getName(), ERROR_INVOKE,
                        "Capability invocation completion failed");
                idempotencyService.fail(definition, context, idempotencyKey, requestDigest, failure);
                return;
            }
            if (completion == null || !completion.isTargetInvoked()) {
                idempotencyService.release(definition, context, idempotencyKey, requestDigest);
                return;
            }
            if (completion.getFailure() != null) {
                CapabilityResult failure = classifyException(definition.getName(), completion.getFailure());
                idempotencyService.fail(definition, context, idempotencyKey, requestDigest, failure);
                return;
            }
            CapabilityResult terminalResult = Objects.requireNonNull(completion.getResult(),
                    "Capability invocation completion result must not be null");
            idempotencyService.complete(definition, context, idempotencyKey, requestDigest, terminalResult);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to settle deferred ACF invocation, traceId={}, capability={}, errorType={}",
                    context.getTraceId(), definition.getName(), exception.getClass().getName());
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

    private record CapabilityInvocationOutcome(CapabilityResult result, Throwable failureCause,
                                               boolean targetInvoked,
                                               CapabilityInvocationHandle deferredHandle) {
    }

}
