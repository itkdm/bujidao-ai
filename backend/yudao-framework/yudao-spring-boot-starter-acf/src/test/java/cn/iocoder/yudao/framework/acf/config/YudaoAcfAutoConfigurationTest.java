package cn.iocoder.yudao.framework.acf.config;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityExecutor;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
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

import static org.assertj.core.api.Assertions.assertThat;

class YudaoAcfAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    ValidationAutoConfiguration.class, YudaoAcfAutoConfiguration.class));

    @Test
    void shouldRegisterCoreBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CapabilitySchemaGenerator.class);
            assertThat(context).hasSingleBean(CapabilityRegistry.class);
            assertThat(context).hasSingleBean(CapabilityExecutor.class);
        });
    }

    @Test
    void shouldDiscoverBusinessCapabilityAutomatically() {
        contextRunner.withUserConfiguration(CapabilityProviderConfig.class)
                .run(context -> {
                    CapabilityRegistry registry = context.getBean(CapabilityRegistry.class);
                    CapabilityExecutor executor = context.getBean(CapabilityExecutor.class);

                    assertThat(registry.get("test.auto.echo").getTitle()).isEqualTo("自动发现能力");
                    assertThat(executor.invoke(CapabilityInvokeCommand.builder()
                            .name("test.auto.echo")
                            .arguments("hello")
                            .build()).getData()).isEqualTo("hello");
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesCoreBeans() {
        contextRunner.withUserConfiguration(CustomCoreBeansConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CapabilitySchemaGenerator.class);
                    assertThat(context).hasSingleBean(CapabilityRegistry.class);
                    assertThat(context).hasSingleBean(CapabilityExecutor.class);
                    assertThat(context).hasBean("customCapabilitySchemaGenerator");
                    assertThat(context).hasBean("customCapabilityRegistry");
                    assertThat(context).hasBean("customCapabilityExecutor");
                    assertThat(context).doesNotHaveBean("capabilitySchemaGenerator");
                    assertThat(context).doesNotHaveBean("capabilityRegistry");
                    assertThat(context).doesNotHaveBean("capabilityExecutor");
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
        CapabilityExecutor customCapabilityExecutor(CapabilityRegistry capabilityRegistry,
                                                    ObjectMapper objectMapper, Validator validator) {
            return new CapabilityExecutor(capabilityRegistry, objectMapper, validator);
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

}
