package cn.iocoder.yudao.framework.acf.config;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityAuditRecord;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationChallenge;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationCheck;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicy;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyChain;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyContext;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyDecision;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPermissionPolicy;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityAuditService;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityExecutor;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityGovernanceService;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityIdempotencyService;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityPermissionEvaluator;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRequestDigestGenerator;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityVisibilityService;
import cn.iocoder.yudao.framework.acf.core.service.DefaultCapabilityGovernanceService;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YudaoAcfAutoConfigurationTest {

    private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    ValidationAutoConfiguration.class, YudaoAcfAutoConfiguration.class));
    private final ApplicationContextRunner contextRunner = baseContextRunner
            .withBean(PermissionCommonApi.class, YudaoAcfAutoConfigurationTest::allowPermissionCommonApi);

    @Test
    void shouldRegisterCoreBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CapabilitySchemaGenerator.class);
            assertThat(context).hasSingleBean(CapabilityRegistry.class);
            assertThat(context).hasSingleBean(CapabilityPermissionEvaluator.class);
            assertThat(context).hasSingleBean(CapabilityPermissionPolicy.class);
            assertThat(context).hasSingleBean(CapabilityPolicyChain.class);
            assertThat(context).hasSingleBean(CapabilityGovernanceService.class);
            assertThat(context).hasSingleBean(CapabilityVisibilityService.class);
            assertThat(context).hasSingleBean(CapabilityRequestDigestGenerator.class);
            assertThat(context).hasSingleBean(CapabilityExecutor.class);
            assertThat(context).doesNotHaveBean(CapabilityConfirmationService.class);
            assertThat(context).doesNotHaveBean(CapabilityIdempotencyService.class);
            assertThat(context).doesNotHaveBean(CapabilityAuditService.class);
        });
    }

    @Test
    void shouldUseBusinessAuditServiceWhenProvided() {
        contextRunner.withUserConfiguration(CapabilityProviderConfig.class, AuditServiceConfig.class)
                .run(context -> {
                    CapabilityResult result = context.getBean(CapabilityExecutor.class)
                            .invoke(CapabilityInvokeCommand.builder()
                                    .name("test.auto.echo")
                                    .arguments("hello")
                                    .context(CapabilityContext.builder().userId(1L).build())
                                    .build());

                    CapturingAuditService auditService = context.getBean(CapturingAuditService.class);
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(auditService.record).isNotNull();
                    assertThat(auditService.record.getTraceId()).isEqualTo(result.getTraceId());
                    assertThat(auditService.record.getCapabilityName()).isEqualTo("test.auto.echo");
                });
    }

    @Test
    void shouldUseBusinessConfirmationServiceWhenProvided() {
        contextRunner.withUserConfiguration(ConfirmedCapabilityConfig.class, ConfirmationServiceConfig.class)
                .run(context -> {
                    CapabilityResult result = context.getBean(CapabilityExecutor.class)
                            .invoke(CapabilityInvokeCommand.builder()
                                    .name("test.auto.order.update")
                                    .arguments("confirmed")
                                    .context(CapabilityContext.builder().userId(1L).build())
                                    .idempotencyKey("idem-auto-001")
                                    .build());

                    assertThat(result.getStatus()).isEqualTo(CapabilityStatus.CONFIRM_REQUIRED);
                    assertThat(result.getData()).isInstanceOf(CapabilityConfirmationChallenge.class);
                });
    }

    @Test
    void shouldDiscoverBusinessCapabilityAutomatically() {
        contextRunner.withUserConfiguration(CapabilityProviderConfig.class)
                .run(context -> {
                    CapabilityRegistry registry = context.getBean(CapabilityRegistry.class);
                    CapabilityVisibilityService visibilityService = context.getBean(CapabilityVisibilityService.class);
                    CapabilityExecutor executor = context.getBean(CapabilityExecutor.class);
                    CapabilityContext invocationContext = CapabilityContext.builder().userId(1L).build();

                    assertThat(registry.get("test.auto.echo").getTitle()).isEqualTo("自动发现能力");
                    assertThat(visibilityService.listVisible(CapabilityVisibilityQuery.builder()
                            .context(invocationContext)
                            .build())).extracting(CapabilityDefinition::getName)
                            .containsExactly("test.auto.echo");
                    assertThat(executor.invoke(CapabilityInvokeCommand.builder()
                            .name("test.auto.echo")
                            .arguments("hello")
                            .context(invocationContext)
                            .build()).getData()).isEqualTo("hello");
                });
    }

    @Test
    void shouldFailClosedWhenPermissionApiIsMissing() {
        baseContextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining(PermissionCommonApi.class.getName());
        });
    }

    @Test
    void shouldDenyCapabilityExecutionWithoutRequiredPermission() {
        baseContextRunner.withBean(PermissionCommonApi.class, YudaoAcfAutoConfigurationTest::denyPermissionCommonApi)
                .withUserConfiguration(CapabilityProviderConfig.class)
                .run(context -> {
                    CapabilityExecutor executor = context.getBean(CapabilityExecutor.class);

                    assertThat(executor.invoke(CapabilityInvokeCommand.builder()
                            .name("test.auto.echo")
                            .arguments("hello")
                            .context(CapabilityContext.builder().userId(1L).build())
                            .build()).getErrorCode()).isEqualTo(CapabilityPermissionPolicy.ERROR_PERMISSION_DENIED);
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesCoreBeans() {
        contextRunner.withUserConfiguration(CustomCoreBeansConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CapabilitySchemaGenerator.class);
                    assertThat(context).hasSingleBean(CapabilityRegistry.class);
                    assertThat(context).hasSingleBean(CapabilityPolicyChain.class);
                    assertThat(context).hasSingleBean(CapabilityGovernanceService.class);
                    assertThat(context).hasSingleBean(CapabilityVisibilityService.class);
                    assertThat(context).hasSingleBean(CapabilityRequestDigestGenerator.class);
                    assertThat(context).hasSingleBean(CapabilityExecutor.class);
                    assertThat(context).hasBean("customCapabilitySchemaGenerator");
                    assertThat(context).hasBean("customCapabilityRegistry");
                    assertThat(context).hasBean("customCapabilityPolicyChain");
                    assertThat(context).hasBean("customCapabilityGovernanceService");
                    assertThat(context).hasBean("customCapabilityVisibilityService");
                    assertThat(context).hasBean("customCapabilityRequestDigestGenerator");
                    assertThat(context).hasBean("customCapabilityExecutor");
                    assertThat(context).doesNotHaveBean("capabilitySchemaGenerator");
                    assertThat(context).doesNotHaveBean("capabilityRegistry");
                    assertThat(context).doesNotHaveBean("capabilityPolicyChain");
                    assertThat(context).doesNotHaveBean("capabilityGovernanceService");
                    assertThat(context).doesNotHaveBean("capabilityVisibilityService");
                    assertThat(context).doesNotHaveBean("capabilityRequestDigestGenerator");
                    assertThat(context).doesNotHaveBean("capabilityExecutor");
                });
    }

    @Test
    void shouldCollectBusinessPoliciesAutomatically() {
        contextRunner.withUserConfiguration(CapabilityProviderConfig.class, DenyPolicyConfig.class)
                .run(context -> {
                    CapabilityExecutor executor = context.getBean(CapabilityExecutor.class);

                    assertThat(executor.invoke(CapabilityInvokeCommand.builder()
                            .name("test.auto.echo")
                            .arguments("hello")
                            .context(CapabilityContext.builder().userId(1L).build())
                            .build()).getErrorCode()).isEqualTo("AUTO_DENIED");
                });
    }

    @Test
    void shouldFailContextWhenCapabilityNamesAreDuplicated() {
        contextRunner.withUserConfiguration(DuplicateCapabilityConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Duplicate ACF capability name 'test.auto.duplicate'");
                });
    }

    @Test
    void shouldDeclareAutoConfigurationImport() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(YudaoAcfAutoConfiguration.class.getName());
    }

    @Configuration(proxyBeanMethods = false)
    static class CapabilityProviderConfig {

        @Bean
        EchoCapability echoCapability() {
            return new EchoCapability();
        }
    }

    static class EchoCapability {

        @AgentCapability(name = "test.auto.echo", title = "自动发现能力", description = "验证自动配置扫描",
                permissions = "test:auto:echo")
        public String echo(String value) {
            return value;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfirmedCapabilityConfig {

        @Bean
        ConfirmedCapability confirmedCapability() {
            return new ConfirmedCapability();
        }
    }

    static class ConfirmedCapability {

        @AgentCapability(name = "test.auto.order.update", title = "确认更新订单",
                description = "验证确认服务自动装配", permissions = "test:auto:order:update",
                sideEffect = true, confirmationRequired = true)
        public String update(String value) {
            return value;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfirmationServiceConfig {

        @Bean
        CapabilityConfirmationService capabilityConfirmationService() {
            return new CapabilityConfirmationService() {
                @Override
                public CapabilityConfirmationChallenge createChallenge(CapabilityDefinition definition,
                                                                       CapabilityContext context,
                                                                       String idempotencyKey,
                                                                       String requestDigest) {
                    return CapabilityConfirmationChallenge.builder()
                            .challengeId("acf-confirm-auto")
                            .capabilityName(definition.getName())
                            .capabilityVersion(definition.getVersion())
                            .riskLevel(definition.getRiskLevel())
                            .requestDigest(requestDigest)
                            .build();
                }

                @Override
                public CapabilityConfirmationCheck verifyAndConsumeToken(CapabilityDefinition definition,
                                                                         CapabilityContext context,
                                                                         String confirmationToken,
                                                                         String idempotencyKey,
                                                                         String requestDigest) {
                    return CapabilityConfirmationCheck.valid("acf-confirm-auto");
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuditServiceConfig {

        @Bean
        CapturingAuditService capabilityAuditService() {
            return new CapturingAuditService();
        }
    }

    static class CapturingAuditService implements CapabilityAuditService {

        private CapabilityAuditRecord record;

        @Override
        public void record(CapabilityAuditRecord record) {
            this.record = record;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCoreBeansConfig {

        @Bean
        CapabilitySchemaGenerator customCapabilitySchemaGenerator() {
            return new CapabilitySchemaGenerator();
        }

        @Bean
        CapabilityRegistry customCapabilityRegistry(ApplicationContext applicationContext,
                                                    CapabilitySchemaGenerator schemaGenerator) {
            return new CapabilityRegistry(applicationContext, schemaGenerator);
        }

        @Bean
        CapabilityPolicyChain customCapabilityPolicyChain() {
            return new CapabilityPolicyChain(List.of());
        }

        @Bean
        CapabilityGovernanceService customCapabilityGovernanceService(CapabilityPolicyChain policyChain) {
            return new DefaultCapabilityGovernanceService(policyChain);
        }

        @Bean
        CapabilityVisibilityService customCapabilityVisibilityService(CapabilityRegistry capabilityRegistry,
                                                                      CapabilityGovernanceService governanceService) {
            return new CapabilityVisibilityService(capabilityRegistry, governanceService);
        }

        @Bean
        CapabilityRequestDigestGenerator customCapabilityRequestDigestGenerator(ObjectMapper objectMapper) {
            return new CapabilityRequestDigestGenerator(objectMapper);
        }

        @Bean
        CapabilityExecutor customCapabilityExecutor(CapabilityRegistry capabilityRegistry,
                                                    CapabilityGovernanceService governanceService,
                                                    ObjectMapper objectMapper, Validator validator) {
            return new CapabilityExecutor(capabilityRegistry, governanceService, objectMapper, validator);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DenyPolicyConfig {

        @Bean
        CapabilityPolicy autoDenyPolicy() {
            return new CapabilityPolicy() {
                @Override
                public String code() {
                    return "AUTO_DENY";
                }

                @Override
                public int order() {
                    return 100;
                }

                @Override
                public CapabilityPolicyDecision evaluate(CapabilityPolicyContext context) {
                    return CapabilityPolicyDecision.deny(code(), "AUTO_DENIED", "denied by test policy");
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateCapabilityConfig {

        @Bean
        DuplicateCapability firstCapability() {
            return new DuplicateCapability();
        }

        @Bean
        DuplicateCapability secondCapability() {
            return new DuplicateCapability();
        }
    }

    static class DuplicateCapability {

        @AgentCapability(name = "test.auto.duplicate", title = "重复能力", description = "验证重复能力启动失败",
                permissions = "test:auto:duplicate")
        public void invoke() {
        }
    }

    private static PermissionCommonApi allowPermissionCommonApi() {
        PermissionCommonApi permissionCommonApi = mock(PermissionCommonApi.class);
        when(permissionCommonApi.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        return permissionCommonApi;
    }

    private static PermissionCommonApi denyPermissionCommonApi() {
        return mock(PermissionCommonApi.class);
    }

}
