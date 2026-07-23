package cn.iocoder.yudao.framework.acf.config;

import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicy;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPolicyChain;
import cn.iocoder.yudao.framework.acf.core.policy.CapabilityPermissionPolicy;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * ACF 核心自动配置
 *
 * @author bujidao
 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, ValidationAutoConfiguration.class})
public class YudaoAcfAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CapabilitySchemaGenerator.class)
    public CapabilitySchemaGenerator capabilitySchemaGenerator() {
        return new CapabilitySchemaGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityRegistry.class)
    public CapabilityRegistry capabilityRegistry(ApplicationContext applicationContext,
                                                   CapabilitySchemaGenerator schemaGenerator) {
        return new CapabilityRegistry(applicationContext, schemaGenerator);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityPermissionEvaluator.class)
    public CapabilityPermissionEvaluator capabilityPermissionEvaluator(PermissionCommonApi permissionCommonApi) {
        return new CapabilityPermissionEvaluator(permissionCommonApi);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityPermissionPolicy.class)
    public CapabilityPermissionPolicy capabilityPermissionPolicy(CapabilityPermissionEvaluator permissionEvaluator) {
        return new CapabilityPermissionPolicy(permissionEvaluator);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityPolicyChain.class)
    public CapabilityPolicyChain capabilityPolicyChain(ObjectProvider<CapabilityPolicy> policies) {
        return new CapabilityPolicyChain(policies.stream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityGovernanceService.class)
    public CapabilityGovernanceService capabilityGovernanceService(CapabilityPolicyChain policyChain) {
        return new DefaultCapabilityGovernanceService(policyChain);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityVisibilityService.class)
    public CapabilityVisibilityService capabilityVisibilityService(CapabilityRegistry capabilityRegistry,
                                                                   CapabilityGovernanceService governanceService) {
        return new CapabilityVisibilityService(capabilityRegistry, governanceService);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityRequestDigestGenerator.class)
    public CapabilityRequestDigestGenerator capabilityRequestDigestGenerator(ObjectMapper objectMapper) {
        return new CapabilityRequestDigestGenerator(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityExecutor.class)
    public CapabilityExecutor capabilityExecutor(CapabilityRegistry capabilityRegistry,
                                                 CapabilityGovernanceService governanceService,
                                                 ObjectProvider<CapabilityConfirmationService> confirmationService,
                                                 ObjectProvider<CapabilityIdempotencyService> idempotencyService,
                                                 CapabilityRequestDigestGenerator requestDigestGenerator,
                                                 ObjectMapper objectMapper, Validator validator) {
        return new CapabilityExecutor(capabilityRegistry, governanceService, confirmationService.getIfAvailable(),
                idempotencyService.getIfAvailable(), requestDigestGenerator, objectMapper, validator);
    }

}
