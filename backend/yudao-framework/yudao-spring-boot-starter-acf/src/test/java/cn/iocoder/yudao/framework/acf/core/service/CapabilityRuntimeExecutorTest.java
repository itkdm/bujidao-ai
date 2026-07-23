package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyChain;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityCircuitBreakerGuard;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuard;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardChain;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardContext;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimeGuardResult;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRateLimitGuard;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityExceptionClassification;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityInvocationExecutor;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimePolicy;
import cn.iocoder.yudao.framework.acf.core.runtime.CapabilityRuntimePolicyService;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityInvocationExecutor;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.standard.AcfCapabilityErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRuntimeExecutorTest {

    private AnnotationConfigApplicationContext applicationContext;
    private ValidatorFactory validatorFactory;
    private CapabilityRegistry registry;
    private ObjectMapper objectMapper;
    private Validator validator;
    private RuntimeCapability capability;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        capability = new RuntimeCapability();
        applicationContext.registerBean("runtimeCapability", RuntimeCapability.class, () -> capability);
        applicationContext.refresh();

        validatorFactory = Validation.buildDefaultValidatorFactory();
        registry = new CapabilityRegistry(applicationContext, new CapabilitySchemaGenerator());
        objectMapper = new ObjectMapper();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
        applicationContext.close();
    }

    @Test
    void shouldRejectBeforeTargetAndReleaseIdempotencyExecutionRight() {
        CapturingRuntimeGuard guard = CapturingRuntimeGuard.rejected();
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        CapturingAuditService auditService = new CapturingAuditService();

        CapabilityResult result = executor(runtimePolicy(), guard, idempotencyService, auditService)
                .invoke(command("test.runtime.success", "runtime-001"));

        assertThat(result.getErrorCode()).as("result message: %s", result.getMessage())
                .isEqualTo("RUNTIME_TEST_REJECTED");
        assertThat(capability.invocationCount).isZero();
        assertThat(idempotencyService.releaseCount).isOne();
        assertThat(idempotencyService.failCount).isZero();
        assertThat(guard.acquireCount).isOne();
        assertThat(guard.releaseCount).isZero();
        assertThat(auditService.record.getRuntimePolicySummary()).contains("maxConcurrency=1");
        assertThat(auditService.record.getRuntimeGuardCode()).isEqualTo(CapturingRuntimeGuard.CODE);
        assertThat(auditService.record.isTargetInvoked()).isFalse();
    }

    @Test
    void shouldNotifyGuardSuccessExactlyOnceAfterTargetReturns() {
        CapturingRuntimeGuard guard = CapturingRuntimeGuard.allowed();

        CapabilityResult result = executor(runtimePolicy(), guard, null, null)
                .invoke(command("test.runtime.success", null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(capability.invocationCount).isOne();
        assertThat(guard.successCount).isOne();
        assertThat(guard.failureCount).isZero();
        assertThat(guard.releaseCount).isZero();
    }

    @Test
    void shouldNotifyGuardFailureWhenTargetThrowsOrReturnsFailure() {
        CapturingRuntimeGuard throwingGuard = CapturingRuntimeGuard.allowed();
        CapabilityResult thrownResult = executor(runtimePolicy(), throwingGuard, null, null)
                .invoke(command("test.runtime.throwing", null));

        assertThat(thrownResult.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_INVOKE);
        assertThat(throwingGuard.failureCount).isOne();
        assertThat(throwingGuard.failureCause).isNotNull();

        CapturingRuntimeGuard failureResultGuard = CapturingRuntimeGuard.allowed();
        CapabilityResult failureResult = executor(runtimePolicy(), failureResultGuard, null, null)
                .invoke(command("test.runtime.reject", null));

        assertThat(failureResult.getErrorCode()).isEqualTo("BUSINESS_REJECTED");
        assertThat(failureResultGuard.failureCount).isOne();
        assertThat(failureResultGuard.failureCause).isNull();
    }

    @Test
    void shouldReleaseIdempotencyWhenRuntimePolicyResolutionFails() {
        CapturingRuntimeGuard guard = CapturingRuntimeGuard.allowed();
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        CapabilityRuntimePolicyService failingPolicyService = (definition, context) -> {
            throw new IllegalStateException("runtime policy unavailable");
        };

        CapabilityResult result = executor(failingPolicyService, guard, idempotencyService, null)
                .invoke(command("test.runtime.success", "runtime-002"));

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_RUNTIME_POLICY);
        assertThat(result.getMessage()).isEqualTo("runtime policy unavailable");
        assertThat(capability.invocationCount).isZero();
        assertThat(guard.acquireCount).isZero();
        assertThat(idempotencyService.releaseCount).isOne();
    }

    @Test
    void shouldTimeoutTargetAndCloseRuntimeLifecycleAsFailure() throws Exception {
        CapturingRuntimeGuard guard = CapturingRuntimeGuard.allowed();
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        CapturingAuditService auditService = new CapturingAuditService();

        try (DefaultCapabilityInvocationExecutor invocationExecutor =
                     new DefaultCapabilityInvocationExecutor()) {
            CapabilityResult result = executor(retryPolicy(30), guard, idempotencyService,
                    auditService, invocationExecutor).invoke(command("test.runtime.slow", "runtime-003"));

            assertThat(result.getErrorCode()).isEqualTo("RUNTIME_TIMEOUT");
            assertThat(result.isRetryable()).isTrue();
            assertThat(capability.invocationCount).isOne();
            assertThat(capability.slowInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(guard.failureCount).isOne();
            assertThat(guard.failureCause).isInstanceOf(TimeoutException.class);
            assertThat(idempotencyService.failCount).isOne();
            assertThat(idempotencyService.releaseCount).isZero();
            assertThat(auditService.record.getRetryCount()).isZero();
            assertThat(auditService.record.getRuntimePolicySummary()).contains("timeoutMs=30");
            assertThat(auditService.record.isTargetInvoked()).isTrue();
        }
    }

    @Test
    void shouldRetryRetryableReadOnlyFailureAndCompleteIdempotencyOnce() {
        CapturingRuntimeGuard guard = CapturingRuntimeGuard.allowed();
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        CapturingAuditService auditService = new CapturingAuditService();

        CapabilityResult result = executor(retryPolicy(1_000), guard, idempotencyService, auditService,
                DefaultCapabilityInvocationExecutor.shared(), retryableClassifier())
                .invoke(command("test.runtime.flaky", "runtime-004"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(capability.invocationCount).isEqualTo(2);
        assertThat(guard.successCount).isOne();
        assertThat(guard.failureCount).isZero();
        assertThat(idempotencyService.completeCount).isOne();
        assertThat(idempotencyService.failCount).isZero();
        assertThat(auditService.record.getRetryCount()).isOne();
    }

    @Test
    void shouldNotRetrySideEffectAfterTargetHasStarted() {
        CapturingAuditService auditService = new CapturingAuditService();

        CapabilityResult result = executor(retryPolicy(1_000), CapturingRuntimeGuard.allowed(),
                null, auditService, DefaultCapabilityInvocationExecutor.shared(), retryableClassifier())
                .invoke(command("test.runtime.mutate", null));

        assertThat(result.getErrorCode()).isEqualTo("TRANSIENT_FAILURE");
        assertThat(capability.invocationCount).isOne();
        assertThat(auditService.record.getRetryCount()).isZero();
    }

    @Test
    void shouldRetrySideEffectWhenExecutorRejectsBeforeTargetStarts() {
        CapturingAuditService auditService = new CapturingAuditService();
        int[] submitCount = {0};
        CapabilityInvocationExecutor rejectingOnceExecutor = (invocation, timeoutMs) -> {
            if (submitCount[0]++ == 0) {
                throw new RejectedExecutionException("executor saturated");
            }
            return invocation.call();
        };

        CapabilityResult result = executor(retryPolicy(1_000), CapturingRuntimeGuard.allowed(),
                null, auditService, rejectingOnceExecutor, new DefaultCapabilityExceptionClassifier())
                .invoke(command("test.runtime.update", null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(submitCount[0]).isEqualTo(2);
        assertThat(capability.invocationCount).isOne();
        assertThat(auditService.record.getRetryCount()).isOne();
        assertThat(auditService.record.isTargetInvoked()).isTrue();
    }

    @Test
    void shouldStopRetryingAfterConfiguredMaximumAttempts() {
        CapturingAuditService auditService = new CapturingAuditService();

        CapabilityResult result = executor(retryPolicy(1_000), CapturingRuntimeGuard.allowed(),
                null, auditService, DefaultCapabilityInvocationExecutor.shared(), retryableClassifier())
                .invoke(command("test.runtime.unstable", null));

        assertThat(result.getErrorCode()).isEqualTo("TRANSIENT_FAILURE");
        assertThat(capability.invocationCount).isEqualTo(3);
        assertThat(auditService.record.getRetryCount()).isEqualTo(2);
    }

    @Test
    void shouldRejectRateLimitedInvocationBeforeTargetStarts() {
        CapturingAuditService auditService = new CapturingAuditService();
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.builder()
                .timeoutMs(1_000)
                .rateLimitEnabled(true)
                .rateLimitCount(1)
                .rateLimitWindowSeconds(60)
                .build();
        CapabilityExecutor rateLimitedExecutor = executor((definition, context) -> policy,
                new CapabilityRateLimitGuard(), null, auditService);

        CapabilityResult first = rateLimitedExecutor.invoke(command("test.runtime.success", null));
        CapabilityResult second = rateLimitedExecutor.invoke(command("test.runtime.success", null));

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.getErrorCode()).isEqualTo("RUNTIME_RATE_LIMITED");
        assertThat(second.isRetryable()).isTrue();
        assertThat(capability.invocationCount).isOne();
        assertThat(auditService.record.getRuntimeGuardCode()).isEqualTo(CapabilityRateLimitGuard.CODE);
        assertThat(auditService.record.isTargetInvoked()).isFalse();
    }

    @Test
    void shouldRejectInvocationBeforeTargetWhenCircuitIsOpen() {
        CapturingAuditService auditService = new CapturingAuditService();
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.builder()
                .timeoutMs(1_000)
                .circuitBreakerEnabled(true)
                .circuitFailureThreshold(2)
                .circuitOpenSeconds(60)
                .circuitHalfOpenMaxCalls(1)
                .build();
        CapabilityExecutor circuitProtectedExecutor = executor((definition, context) -> policy,
                new CapabilityCircuitBreakerGuard(), null, auditService);

        CapabilityResult first = circuitProtectedExecutor.invoke(command("test.runtime.throwing", null));
        CapabilityResult second = circuitProtectedExecutor.invoke(command("test.runtime.throwing", null));
        CapabilityResult rejected = circuitProtectedExecutor.invoke(command("test.runtime.throwing", null));

        assertThat(first.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.INVOKE_ERROR);
        assertThat(second.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.INVOKE_ERROR);
        assertThat(rejected.getErrorCode()).isEqualTo(AcfCapabilityErrorCodes.RUNTIME_CIRCUIT_OPEN);
        assertThat(rejected.isRetryable()).isTrue();
        assertThat(capability.invocationCount).isEqualTo(2);
        assertThat(auditService.record.getRuntimeGuardCode()).isEqualTo(CapabilityCircuitBreakerGuard.CODE);
        assertThat(auditService.record.isTargetInvoked()).isFalse();
    }

    private CapabilityExecutor executor(CapabilityRuntimePolicyService runtimePolicyService,
                                        CapabilityRuntimeGuard guard,
                                        CapabilityIdempotencyService idempotencyService,
                                        CapabilityAuditService auditService) {
        return executor(runtimePolicyService, guard, idempotencyService, auditService,
                DefaultCapabilityInvocationExecutor.shared());
    }

    private CapabilityExecutor executor(CapabilityRuntimePolicyService runtimePolicyService,
                                        CapabilityRuntimeGuard guard,
                                        CapabilityIdempotencyService idempotencyService,
                                        CapabilityAuditService auditService,
                                        CapabilityInvocationExecutor invocationExecutor) {
        return executor(runtimePolicyService, guard, idempotencyService, auditService,
                invocationExecutor, new DefaultCapabilityExceptionClassifier());
    }

    private CapabilityExecutor executor(CapabilityRuntimePolicyService runtimePolicyService,
                                        CapabilityRuntimeGuard guard,
                                        CapabilityIdempotencyService idempotencyService,
                                        CapabilityAuditService auditService,
                                        CapabilityInvocationExecutor invocationExecutor,
                                        CapabilityExceptionClassifier exceptionClassifier) {
        CapabilityGovernanceService governanceService = new DefaultCapabilityGovernanceService(
                new CapabilityPolicyChain(List.of()));
        return new CapabilityExecutor(registry, governanceService, null, idempotencyService, auditService,
                exceptionClassifier, runtimePolicyService,
                new CapabilityRuntimeGuardChain(List.of(guard)), invocationExecutor,
                new CapabilityRequestDigestGenerator(objectMapper), objectMapper, validator);
    }

    private CapabilityRuntimePolicyService runtimePolicy() {
        return runtimePolicy(2_000);
    }

    private CapabilityRuntimePolicyService runtimePolicy(int timeoutMs) {
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.builder()
                .timeoutMs(timeoutMs)
                .maxConcurrency(1)
                .build();
        return (definition, context) -> policy;
    }

    private CapabilityRuntimePolicyService retryPolicy(int timeoutMs) {
        CapabilityRuntimePolicy policy = CapabilityRuntimePolicy.builder()
                .timeoutMs(timeoutMs)
                .maxConcurrency(1)
                .retryEnabled(true)
                .retryMaxAttempts(3)
                .retryBackoffMs(1)
                .build();
        return (definition, context) -> policy;
    }

    private CapabilityExceptionClassifier retryableClassifier() {
        return throwable -> CapabilityExceptionClassification.of(
                "TRANSIENT_FAILURE", "transient failure", true, throwable);
    }

    private CapabilityInvokeCommand command(String name, String idempotencyKey) {
        return CapabilityInvokeCommand.builder()
                .name(name)
                .arguments(Map.of())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    static class RuntimeCapability {

        private int invocationCount;
        private final CountDownLatch slowInterrupted = new CountDownLatch(1);

        @AgentCapability(name = "test.runtime.success", title = "运行时成功能力",
                description = "验证运行时保护器成功收口", permissions = "test:runtime:invoke")
        public String success() {
            invocationCount++;
            return "ok";
        }

        @AgentCapability(name = "test.runtime.throwing", title = "运行时异常能力",
                description = "验证运行时保护器异常收口", permissions = "test:runtime:invoke")
        public String throwing() {
            invocationCount++;
            throw new IllegalStateException("target failed");
        }

        @AgentCapability(name = "test.runtime.reject", title = "运行时失败结果能力",
                description = "验证标准失败结果收口", permissions = "test:runtime:invoke")
        public CapabilityResult failureResult() {
            invocationCount++;
            return CapabilityResult.failure("BUSINESS_REJECTED", "business rejected", false);
        }

        @AgentCapability(name = "test.runtime.slow", title = "运行时超时能力",
                description = "验证目标调用超时与中断", permissions = "test:runtime:invoke")
        public String slow() throws InterruptedException {
            invocationCount++;
            try {
                Thread.sleep(10_000);
                return "late";
            } catch (InterruptedException exception) {
                slowInterrupted.countDown();
                throw exception;
            }
        }

        @AgentCapability(name = "test.runtime.flaky", title = "运行时瞬时失败能力",
                description = "验证只读能力受控重试", permissions = "test:runtime:invoke")
        public String flaky() {
            invocationCount++;
            if (invocationCount == 1) {
                throw new IllegalStateException("transient failure");
            }
            return "ok";
        }

        @AgentCapability(name = "test.runtime.mutate", title = "运行时副作用失败能力",
                description = "验证副作用能力禁止危险重试", permissions = "test:runtime:invoke", sideEffect = true)
        public String mutate() {
            invocationCount++;
            throw new IllegalStateException("transient failure");
        }

        @AgentCapability(name = "test.runtime.update", title = "运行时副作用更新能力",
                description = "验证目标启动前允许安全重试", permissions = "test:runtime:invoke", sideEffect = true)
        public String update() {
            invocationCount++;
            return "updated";
        }

        @AgentCapability(name = "test.runtime.unstable", title = "运行时持续失败能力",
                description = "验证重试达到最大次数后停止", permissions = "test:runtime:invoke")
        public String unstable() {
            invocationCount++;
            throw new IllegalStateException("transient failure");
        }
    }

    static class CapturingRuntimeGuard implements CapabilityRuntimeGuard {

        static final String CODE = "TEST_RUNTIME";

        private final boolean allowed;
        private int acquireCount;
        private int releaseCount;
        private int successCount;
        private int failureCount;
        private Throwable failureCause;

        private CapturingRuntimeGuard(boolean allowed) {
            this.allowed = allowed;
        }

        static CapturingRuntimeGuard allowed() {
            return new CapturingRuntimeGuard(true);
        }

        static CapturingRuntimeGuard rejected() {
            return new CapturingRuntimeGuard(false);
        }

        @Override
        public String code() {
            return CODE;
        }

        @Override
        public CapabilityRuntimeGuardResult acquire(CapabilityRuntimeGuardContext context) {
            acquireCount++;
            return allowed ? CapabilityRuntimeGuardResult.allowed(CODE)
                    : CapabilityRuntimeGuardResult.rejected(
                            CODE, "RUNTIME_TEST_REJECTED", "runtime guard rejected", true);
        }

        @Override
        public void release(CapabilityRuntimeGuardContext context) {
            releaseCount++;
        }

        @Override
        public void onSuccess(CapabilityRuntimeGuardContext context, CapabilityResult result) {
            successCount++;
        }

        @Override
        public void onFailure(CapabilityRuntimeGuardContext context, CapabilityResult result, Throwable cause) {
            failureCount++;
            failureCause = cause;
        }
    }

    static class CapturingIdempotencyService implements CapabilityIdempotencyService {

        private int failCount;
        private int releaseCount;
        private int completeCount;

        @Override
        public CapabilityIdempotencyCheck acquire(CapabilityDefinition definition, CapabilityContext context,
                                                  String idempotencyKey, String requestDigest) {
            return CapabilityIdempotencyCheck.acquired();
        }

        @Override
        public void complete(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                             String requestDigest, CapabilityResult result) {
            completeCount++;
        }

        @Override
        public void fail(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                         String requestDigest, CapabilityResult result) {
            failCount++;
        }

        @Override
        public void release(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                            String requestDigest) {
            releaseCount++;
        }
    }

    static class CapturingAuditService implements CapabilityAuditService {

        private CapabilityAuditRecord record;

        @Override
        public void record(CapabilityAuditRecord record) {
            this.record = record;
        }
    }

}
