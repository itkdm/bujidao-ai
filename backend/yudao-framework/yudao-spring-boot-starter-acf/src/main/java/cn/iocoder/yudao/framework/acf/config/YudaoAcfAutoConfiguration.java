package cn.iocoder.yudao.framework.acf.config;

import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * ACF 核心自动配置
 *
 * @author bujidao
 */
@AutoConfiguration
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

}
