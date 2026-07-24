package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStage;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityAuditStepStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConfirmationStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityIdempotencyAuditStatus;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditStepRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyChain;
import cn.iocoder.yudao.framework.acf.core.runtime.DefaultCapabilityExceptionClassifier;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityAuditExecutorTest {

    private AnnotationConfigApplicationContext applicationContext;
    private CapabilityRegistry registry;
    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.registerBean("auditCapability", AuditCapability.class);
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
    void shouldRecordSuccessfulExecutionWithoutBusinessPayload() {
        CapturingAuditService auditService = new CapturingAuditService();
        CapabilityContext context = CapabilityContext.builder()
                .traceId("trace-audit-001")
                .userId(10L)
                .tenantId(20L)
                .source("agent-runtime")
                .consumerType(CapabilityConsumerType.AGENT)
                .consumerId("agent-001")
                .clientRequestId("request-001")
                .build();

        CapabilityResult result = executor(auditService).invoke(CapabilityInvokeCommand.builder()
                .name("test.audit.echo")
                .arguments("hello")
                .context(context)
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTraceId()).isEqualTo("trace-audit-001");
        CapabilityAuditRecord record = auditService.record;
        assertThat(record.getTraceId()).isEqualTo(result.getTraceId());
        assertThat(record.getCapabilityName()).isEqualTo("test.audit.echo");
        assertThat(record.getCapabilityVersion()).isEqualTo("1.0.0");
        assertThat(record.getUserId()).isEqualTo(10L);
        assertThat(record.getTenantId()).isEqualTo(20L);
        assertThat(record.getSource()).isEqualTo("agent-runtime");
        assertThat(record.getConsumerType()).isEqualTo(CapabilityConsumerType.AGENT);
        assertThat(record.getConsumerId()).isEqualTo("agent-001");
        assertThat(record.getClientRequestId()).isEqualTo("request-001");
        assertThat(record.getFinalStage()).isEqualTo(CapabilityAuditStage.COMPLETED);
        assertThat(record.getConfirmationStatus()).isEqualTo(CapabilityConfirmationStatus.NOT_REQUIRED);
        assertThat(record.getIdempotencyStatus()).isEqualTo(CapabilityIdempotencyAuditStatus.NOT_REQUESTED);
        assertThat(record.getRuntimePolicySummary()).isEqualTo("timeoutMs=30000");
        assertThat(record.getRuntimeGuardCode()).isNull();
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.isTargetInvoked()).isTrue();
        assertThat(record.getStatus()).isEqualTo(CapabilityStatus.SUCCESS);
        assertThat(record.getErrorCode()).isNull();
        assertThat(record.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(auditService.steps)
                .extracting(CapabilityAuditStepRecord::getStage)
                .containsExactly(CapabilityAuditStage.REQUEST_VALIDATION,
                        CapabilityAuditStage.CAPABILITY_LOOKUP,
                        CapabilityAuditStage.GOVERNANCE,
                        CapabilityAuditStage.ARGUMENT_VALIDATION,
                        CapabilityAuditStage.CONFIRMATION,
                        CapabilityAuditStage.IDEMPOTENCY,
                        CapabilityAuditStage.RUNTIME_POLICY,
                        CapabilityAuditStage.RUNTIME_GUARD,
                        CapabilityAuditStage.INVOCATION);
        assertThat(auditService.steps)
                .extracting(CapabilityAuditStepRecord::getStepNo)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(auditService.steps)
                .extracting(CapabilityAuditStepRecord::getStatus)
                .containsOnly(CapabilityAuditStepStatus.SUCCESS);
    }

    @Test
    void shouldAuditFailureBeforeTargetInvocation() {
        CapturingAuditService auditService = new CapturingAuditService();

        CapabilityResult result = executor(auditService).invoke(CapabilityInvokeCommand.builder()
                .name("test.audit.missing")
                .build());

        assertThat(result.getTraceId()).startsWith("acf-");
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_CAPABILITY_NOT_FOUND);
        assertThat(auditService.record.getFinalStage()).isEqualTo(CapabilityAuditStage.CAPABILITY_LOOKUP);
        assertThat(auditService.record.isTargetInvoked()).isFalse();
        assertThat(auditService.record.getStatus()).isEqualTo(CapabilityStatus.FAILURE);
        assertThat(auditService.steps).hasSize(2);
        assertThat(auditService.steps.get(1).getStatus()).isEqualTo(CapabilityAuditStepStatus.FAILURE);
    }

    @Test
    void shouldDistinguishIdempotencyInfrastructureErrorFromRequestConflict() {
        CapturingAuditService auditService = new CapturingAuditService();

        CapabilityResult result = executor(auditService).invoke(CapabilityInvokeCommand.builder()
                .name("test.audit.echo")
                .arguments("hello")
                .idempotencyKey("idem-audit-001")
                .build());

        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_IDEMPOTENCY_UNAVAILABLE);
        assertThat(auditService.record.getFinalStage()).isEqualTo(CapabilityAuditStage.IDEMPOTENCY);
        assertThat(auditService.record.getIdempotencyStatus()).isEqualTo(CapabilityIdempotencyAuditStatus.ERROR);
        assertThat(auditService.record.isTargetInvoked()).isFalse();
    }

    @Test
    void shouldNotChangeCapabilityResultWhenAuditPersistenceFails() {
        CapturingAuditService auditService = new CapturingAuditService();
        auditService.throwOnRecord = true;

        CapabilityResult result = executor(auditService).invoke(CapabilityInvokeCommand.builder()
                .name("test.audit.echo")
                .arguments("hello")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("hello");
        assertThat(auditService.recordCount).isOne();
    }

    @Test
    void shouldNotExposeInternalExceptionDetailsThroughResultToolOrAudit() {
        CapturingAuditService auditService = new CapturingAuditService();
        CapabilityExecutor executor = executor(auditService);

        CapabilityResult result = new CapabilityToolInvoker(executor).invoke(CapabilityToolCall.builder()
                .capabilityName("test.audit.secret.failure")
                .build());

        assertThat(result.getMessage()).isEqualTo("Capability invocation failed");
        assertThat(result.getMessage()).doesNotContain("SELECT", "internal.example", "Bearer", "C:\\private");
        assertThat(auditService.record.getMessage()).isEqualTo("Capability invocation failed");
        assertThat(auditService.steps).allSatisfy(step -> {
            if (step.getSummary() != null) {
                assertThat(step.getSummary())
                        .doesNotContain("SELECT", "internal.example", "Bearer", "C:\\private");
            }
            if (step.getErrorMessage() != null) {
                assertThat(step.getErrorMessage())
                        .doesNotContain("SELECT", "internal.example", "Bearer", "C:\\private");
            }
        });
    }

    private CapabilityExecutor executor(CapabilityAuditService auditService) {
        return CapabilityExecutorTestFixture.create(registry,
                new DefaultCapabilityGovernanceService(new CapabilityPolicyChain(List.of())),
                null, null, auditService, new DefaultCapabilityExceptionClassifier(), objectMapper, validator);
    }

    static class AuditCapability {

        @AgentCapability(name = "test.audit.echo", title = "审计回显", description = "验证执行审计",
                permissions = "test:audit:invoke", version = "1.0.0")
        public String echo(String value) {
            return value;
        }

        @AgentCapability(name = "test.audit.secret.failure", title = "Secret failure",
                description = "Verifies public error redaction", permissions = "test:audit:invoke")
        public String secretFailure() {
            throw new IllegalStateException(
                    "SELECT * FROM account; https://internal.example C:\\private\\data Bearer token-secret");
        }
    }

    static class CapturingAuditService implements CapabilityAuditService {

        private CapabilityAuditRecord record;
        private final List<CapabilityAuditStepRecord> steps = new ArrayList<>();
        private int recordCount;
        private boolean throwOnRecord;

        @Override
        public void record(CapabilityAuditRecord record) {
            recordCount++;
            if (throwOnRecord) {
                throw new IllegalStateException("audit storage unavailable");
            }
            this.record = record;
        }

        @Override
        public void recordStep(CapabilityAuditStepRecord record) {
            steps.add(record);
        }
    }

}
