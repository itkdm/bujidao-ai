package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityIdempotencyCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyChain;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityIdempotencyExecutorTest {

    private AnnotationConfigApplicationContext applicationContext;
    private OrderCapability capability;
    private CapabilityRegistry registry;
    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        capability = new OrderCapability();
        applicationContext.registerBean("orderCapability", OrderCapability.class, () -> capability);
        applicationContext.refresh();
        registry = new CapabilityRegistry(applicationContext, new CapabilitySchemaGenerator());
        registry.afterSingletonsInstantiated();
        objectMapper = new ObjectMapper();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterEach
    void tearDown() {
        applicationContext.close();
    }

    @Test
    void shouldRejectSideEffectWhenIdempotencyKeyIsAbsent() {
        CapabilityResult result = executor(null, null).invoke(command("test.order.create", null));

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_IDEMPOTENCY_KEY_REQUIRED);
        assertThat(capability.createCount).isZero();
    }

    @Test
    void shouldFailClosedWhenIdempotencyKeyIsProvidedWithoutService() {
        CapabilityResult result = executor(null, null).invoke(command("test.order.create", "idem-001"));

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_IDEMPOTENCY_UNAVAILABLE);
        assertThat(capability.createCount).isZero();
    }

    @Test
    void shouldCompleteAcquiredIdempotentExecution() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();

        CapabilityResult result = executor(null, idempotencyService)
                .invoke(command("test.order.create", "idem-002"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(idempotencyService.acquiredKey).isEqualTo("idem-002");
        assertThat(idempotencyService.requestDigest).startsWith("sha256:");
        assertThat(idempotencyService.completeCount).isOne();
        assertThat(idempotencyService.failCount).isZero();
        assertThat(capability.createCount).isOne();
    }

    @Test
    void shouldExposeUncertainStateWhenCompletedResultCannotBeStored() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        idempotencyService.throwOnComplete = true;

        CapabilityResult result = executor(null, idempotencyService)
                .invoke(command("test.order.create", "idem-complete-failed-001"));

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.FAILURE);
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_IDEMPOTENCY);
        assertThat(result.getMessage()).contains("executed").contains("could not be stored");
        assertThat(result.isRetryable()).isFalse();
        assertThat(idempotencyService.completeCount).isOne();
        assertThat(capability.createCount).isOne();
    }

    @Test
    void shouldReplayCompletedResultWithoutInvokingTargetOrConfirmation() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        idempotencyService.check = CapabilityIdempotencyCheck.replayed(
                CapabilityResult.success("stored.capability", "stored", "replayed"));
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();

        CapabilityResult result = executor(confirmationService, idempotencyService)
                .invoke(command("test.order.confirmed.create", "idem-003", "old-token"));

        assertThat(result.getName()).isEqualTo("test.order.confirmed.create");
        assertThat(result.getData()).isEqualTo("stored");
        assertThat(result.getMessage()).isEqualTo("replayed");
        assertThat(confirmationService.verifyCount).isZero();
        assertThat(idempotencyService.completeCount).isZero();
        assertThat(capability.confirmedCreateCount).isZero();
    }

    @Test
    void shouldReturnConflictWithoutInvokingTarget() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        idempotencyService.check = CapabilityIdempotencyCheck.conflict(
                "IDEMPOTENCY_IN_PROGRESS", "request is processing");

        CapabilityResult result = executor(null, idempotencyService)
                .invoke(command("test.order.create", "idem-004"));

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.FAILURE);
        assertThat(result.getErrorCode()).isEqualTo("IDEMPOTENCY_IN_PROGRESS");
        assertThat(capability.createCount).isZero();
    }

    @Test
    void shouldMarkIdempotencyFailedWhenTargetThrows() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();

        CapabilityResult result = executor(null, idempotencyService)
                .invoke(command("test.order.fail", "idem-005"));

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_INVOKE);
        assertThat(idempotencyService.failCount).isOne();
        assertThat(idempotencyService.completeCount).isZero();
    }

    @Test
    void shouldRequireIdempotencyKeyForConfirmedCapability() {
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();

        CapabilityResult result = executor(confirmationService, new CapturingIdempotencyService())
                .invoke(command("test.order.confirmed.create", null));

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_IDEMPOTENCY_KEY_REQUIRED);
        assertThat(confirmationService.createCount).isZero();
        assertThat(capability.confirmedCreateCount).isZero();
    }

    @Test
    void shouldCreateConfirmationChallengeBeforeAcquiringIdempotency() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();

        CapabilityResult result = executor(confirmationService, idempotencyService)
                .invoke(command("test.order.confirmed.create", "idem-challenge-001"));

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.CONFIRM_REQUIRED);
        assertThat(confirmationService.createCount).isOne();
        assertThat(confirmationService.idempotencyKey).isEqualTo("idem-challenge-001");
        assertThat(idempotencyService.acquiredKey).isNull();
        assertThat(capability.confirmedCreateCount).isZero();
    }

    @Test
    void shouldReleaseExecutionRightWhenConfirmationTokenIsInvalid() {
        CapturingIdempotencyService idempotencyService = new CapturingIdempotencyService();
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();
        confirmationService.check = CapabilityConfirmationCheck.invalid(
                "CONFIRM_TOKEN_INVALID", "token invalid");

        CapabilityResult result = executor(confirmationService, idempotencyService)
                .invoke(command("test.order.confirmed.create", "idem-006", "invalid-token"));

        assertThat(result.getErrorCode()).isEqualTo("CONFIRM_TOKEN_INVALID");
        assertThat(idempotencyService.releaseCount).isOne();
        assertThat(idempotencyService.completeCount).isZero();
        assertThat(capability.confirmedCreateCount).isZero();
    }

    private CapabilityExecutor executor(CapabilityConfirmationService confirmationService,
                                        CapabilityIdempotencyService idempotencyService) {
        return CapabilityExecutorTestFixture.create(registry,
                new DefaultCapabilityGovernanceService(new CapabilityPolicyChain(List.of())),
                confirmationService, idempotencyService, null, new DefaultCapabilityExceptionClassifier(),
                objectMapper, validator);
    }

    private CapabilityInvokeCommand command(String name, String idempotencyKey) {
        return command(name, idempotencyKey, null);
    }

    private CapabilityInvokeCommand command(String name, String idempotencyKey, String confirmationToken) {
        return CapabilityInvokeCommand.builder()
                .name(name)
                .arguments(Map.of("value", "order-001"))
                .idempotencyKey(idempotencyKey)
                .confirmationToken(confirmationToken)
                .build();
    }

    static class OrderCapability {

        private int createCount;
        private int confirmedCreateCount;

        @AgentCapability(name = "test.order.create", title = "创建订单", description = "验证幂等执行",
                permissions = "test:order:create", sideEffect = true)
        public String create(OrderRequest request) {
            createCount++;
            return request.value();
        }

        @AgentCapability(name = "test.order.fail", title = "失败订单", description = "验证幂等失败收口",
                permissions = "test:order:create", sideEffect = true)
        public String fail(OrderRequest request) {
            throw new IllegalStateException("create failed");
        }

        @AgentCapability(name = "test.order.confirmed.create", title = "确认创建订单",
                description = "验证确认与幂等顺序", permissions = "test:order:create",
                sideEffect = true, confirmationRequired = true)
        public String confirmedCreate(OrderRequest request) {
            confirmedCreateCount++;
            return request.value();
        }
    }

    record OrderRequest(@NotBlank String value) {
    }

    static class CapturingIdempotencyService implements CapabilityIdempotencyService {

        private CapabilityIdempotencyCheck check = CapabilityIdempotencyCheck.acquired();
        private String acquiredKey;
        private String requestDigest;
        private int completeCount;
        private int failCount;
        private int releaseCount;
        private boolean throwOnComplete;

        @Override
        public CapabilityIdempotencyCheck acquire(CapabilityDefinition definition, CapabilityContext context,
                                                  String idempotencyKey, String requestDigest) {
            this.acquiredKey = idempotencyKey;
            this.requestDigest = requestDigest;
            return check;
        }

        @Override
        public void complete(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                             String requestDigest, CapabilityResult result) {
            completeCount++;
            if (throwOnComplete) {
                throw new IllegalStateException("storage unavailable");
            }
        }

        @Override
        public void markUncertain(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                                  String requestDigest, CapabilityResult result) {
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

    static class CapturingConfirmationService implements CapabilityConfirmationService {

        private CapabilityConfirmationCheck check = CapabilityConfirmationCheck.valid("acf-confirm-001");
        private String idempotencyKey;
        private int createCount;
        private int verifyCount;

        @Override
        public CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition,
                                                               CapabilityContext context, String idempotencyKey,
                                                               String requestDigest) {
            this.idempotencyKey = idempotencyKey;
            createCount++;
            return CapabilityConfirmationChallenge.builder()
                    .challengeId("acf-confirm-001")
                    .capabilityName(definition.getName())
                    .requestDigest(requestDigest)
                    .build();
        }

        @Override
        public CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition,
                                                                 CapabilityContext context,
                                                                 String confirmationToken,
                                                                 String idempotencyKey,
                                                                 String requestDigest) {
            this.idempotencyKey = idempotencyKey;
            verifyCount++;
            return check;
        }
    }

}
