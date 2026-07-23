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
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityConfirmationExecutorTest {

    private AnnotationConfigApplicationContext applicationContext;
    private ConfirmCapability capability;
    private CapabilityRegistry registry;
    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        capability = new ConfirmCapability();
        applicationContext.registerBean("confirmCapability", ConfirmCapability.class, () -> capability);
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
    void shouldFailClosedWhenConfirmationServiceIsMissing() {
        CapabilityResult result = executor(null).invoke(command("test.order.confirmed.update", "approved", null));

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.FAILURE);
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_CONFIRMATION_UNAVAILABLE);
        assertThat(capability.confirmedInvocationCount).isZero();
    }

    @Test
    void shouldCreateChallengeBeforeConfirmedCapabilityExecution() {
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();
        CapabilityContext context = CapabilityContext.builder().userId(10L).tenantId(20L).build();
        CapabilityInvokeCommand command = command("test.order.confirmed.update", "approved", null);
        command.setContext(context);

        CapabilityResult result = executor(confirmationService).invoke(command);

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.CONFIRM_REQUIRED);
        assertThat(result.getErrorCode()).isEqualTo("CONFIRM_REQUIRED");
        assertThat(result.getData()).isInstanceOf(CapabilityConfirmationChallenge.class);
        CapabilityConfirmationChallenge challenge = (CapabilityConfirmationChallenge) result.getData();
        assertThat(challenge.getChallengeId()).isEqualTo("acf-confirm-001");
        assertThat(challenge.getRequestDigest()).isEqualTo(confirmationService.requestDigest);
        assertThat(confirmationService.context.getTraceId()).isEqualTo(result.getTraceId());
        assertThat(confirmationService.context.getUserId()).isEqualTo(context.getUserId());
        assertThat(confirmationService.context.getTenantId()).isEqualTo(context.getTenantId());
        assertThat(capability.confirmedInvocationCount).isZero();
    }

    @Test
    void shouldExecuteCapabilityAfterTokenIsVerifiedAndConsumed() {
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();
        confirmationService.check = CapabilityConfirmationCheck.valid("acf-confirm-001");

        CapabilityResult result = executor(confirmationService)
                .invoke(command("test.order.confirmed.update", "approved", "acf-token-001"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("approved");
        assertThat(confirmationService.confirmationToken).isEqualTo("acf-token-001");
        assertThat(confirmationService.requestDigest).startsWith("sha256:");
        assertThat(capability.confirmedInvocationCount).isOne();
    }

    @Test
    void shouldRejectInvalidConfirmationToken() {
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();
        confirmationService.check = CapabilityConfirmationCheck.invalid(
                "CONFIRM_TOKEN_EXPIRED", "Confirmation token is expired");

        CapabilityResult result = executor(confirmationService)
                .invoke(command("test.order.confirmed.update", "approved", "acf-token-expired"));

        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.FAILURE);
        assertThat(result.getErrorCode()).isEqualTo("CONFIRM_TOKEN_EXPIRED");
        assertThat(result.getMessage()).isEqualTo("Confirmation token is expired");
        assertThat(capability.confirmedInvocationCount).isZero();
    }

    @Test
    void shouldValidateArgumentsBeforeCreatingChallenge() {
        CapturingConfirmationService confirmationService = new CapturingConfirmationService();

        CapabilityResult result = executor(confirmationService)
                .invoke(command("test.order.confirmed.update", "", null));

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_BAD_REQUEST);
        assertThat(confirmationService.createChallengeCount).isZero();
        assertThat(capability.confirmedInvocationCount).isZero();
    }

    @Test
    void shouldNotInferConfirmationFromSideEffectAlone() {
        CapabilityResult result = executor(null)
                .invoke(command("test.order.direct.update", "updated", null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("updated");
        assertThat(capability.directInvocationCount).isOne();
    }

    private CapabilityExecutor executor(CapabilityConfirmationService confirmationService) {
        return new CapabilityExecutor(registry,
                new DefaultCapabilityGovernanceService(new CapabilityPolicyChain(List.of())),
                confirmationService, new PassThroughIdempotencyService(),
                new CapabilityRequestDigestGenerator(objectMapper), objectMapper, validator);
    }

    private CapabilityInvokeCommand command(String name, String value, String confirmationToken) {
        return CapabilityInvokeCommand.builder()
                .name(name)
                .arguments(Map.of("value", value))
                .confirmationToken(confirmationToken)
                .idempotencyKey(name.contains("confirmed") ? "idem-confirm-001" : null)
                .build();
    }

    static class ConfirmCapability {

        private int confirmedInvocationCount;
        private int directInvocationCount;

        @AgentCapability(name = "test.order.confirmed.update", title = "确认更新订单",
                description = "验证执行前确认门槛", permissions = "test:order:update",
                sideEffect = true, confirmationRequired = true)
        public String confirmedUpdate(UpdateRequest request) {
            confirmedInvocationCount++;
            return request.value();
        }

        @AgentCapability(name = "test.order.direct.update", title = "直接更新订单",
                description = "验证副作用与确认配置相互独立", permissions = "test:order:update", sideEffect = true)
        public String directUpdate(UpdateRequest request) {
            directInvocationCount++;
            return request.value();
        }
    }

    record UpdateRequest(@NotBlank String value) {
    }

    static class CapturingConfirmationService implements CapabilityConfirmationService {

        private CapabilityConfirmationCheck check;
        private CapabilityContext context;
        private String confirmationToken;
        private String requestDigest;
        private int createChallengeCount;

        @Override
        public CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition,
                                                               CapabilityContext context, String idempotencyKey,
                                                               String requestDigest) {
            this.context = context;
            this.requestDigest = requestDigest;
            createChallengeCount++;
            return CapabilityConfirmationChallenge.builder()
                    .challengeId("acf-confirm-001")
                    .capabilityName(definition.getName())
                    .capabilityVersion(definition.getVersion())
                    .riskLevel(definition.getRiskLevel())
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .requestDigest(requestDigest)
                    .build();
        }

        @Override
        public CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition,
                                                                 CapabilityContext context,
                                                                 String confirmationToken, String idempotencyKey,
                                                                 String requestDigest) {
            this.context = context;
            this.confirmationToken = confirmationToken;
            this.requestDigest = requestDigest;
            return check;
        }
    }

    static class PassThroughIdempotencyService implements CapabilityIdempotencyService {

        @Override
        public CapabilityIdempotencyCheck acquire(CapabilityDefinition definition, CapabilityContext context,
                                                  String idempotencyKey, String requestDigest) {
            return CapabilityIdempotencyCheck.acquired();
        }

        @Override
        public void complete(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                             String requestDigest, CapabilityResult result) {
        }

        @Override
        public void fail(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                         String requestDigest, CapabilityResult result) {
        }

        @Override
        public void release(CapabilityDefinition definition, CapabilityContext context, String idempotencyKey,
                            String requestDigest) {
        }
    }

}
