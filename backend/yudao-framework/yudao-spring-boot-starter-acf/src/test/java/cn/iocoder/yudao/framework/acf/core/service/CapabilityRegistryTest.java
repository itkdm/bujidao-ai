package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.annotation.CapabilityField;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRegistryTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldDiscoverCapabilityAndBuildCompleteDefinition() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ValidConfig.class)) {
            CapabilityRegistry registry = createRegistry(context);

            CapabilityDefinition definition = registry.get("test.product.search");
            Map<String, Object> inputProperties = (Map<String, Object>) definition.getInputSchema().get("properties");
            Map<String, Object> outputItems = (Map<String, Object>) definition.getOutputSchema().get("items");

            assertEquals("商品搜索", definition.getTitle());
            assertEquals("根据关键词搜索商品", definition.getDescription());
            assertEquals("product", definition.getCategory());
            assertEquals(List.of("product:query", "product:export"), definition.getPermissions());
            assertEquals(CapabilityPermissionMode.ANY, definition.getPermissionMode());
            assertEquals(CapabilityRiskLevel.MEDIUM, definition.getRiskLevel());
            assertFalse(definition.isSideEffect());
            assertTrue(definition.isConfirmationRequired());
            assertEquals("2.0.0", definition.getVersion());
            assertEquals(5_000, definition.getTimeoutMs());
            assertEquals(ProductSearchRequest.class, definition.getArgumentType());
            assertInstanceOf(ParameterizedType.class, definition.getReturnType());
            assertEquals("商品关键词", ((Map<String, Object>) inputProperties.get("keyword")).get("description"));
            assertEquals("array", definition.getOutputSchema().get("type"));
            assertEquals("object", outputItems.get("type"));

            CapabilityRegistration registration = registry.getRegistration(definition.getName());
            assertSame(context.getBean(ProductCapability.class), registration.bean());
            assertEquals("search", registration.method().getName());
        }
    }

    @Test
    void shouldInitializeLazilyAndReturnDefinitionsInNameOrder() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ValidConfig.class)) {
            CapabilityRegistry registry = createRegistry(context);

            assertEquals(List.of("test.product.get", "test.product.search"),
                    registry.list().stream().map(CapabilityDefinition::getName).toList());
            assertThrows(IllegalArgumentException.class, () -> registry.get("test.product.missing"));
        }
    }

    @Test
    void shouldRejectDuplicateCapabilityNames() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(DuplicateConfig.class)) {
            CapabilityRegistry registry = createRegistry(context);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    registry::afterSingletonsInstantiated);

            assertTrue(exception.getMessage().contains("Duplicate ACF capability name 'test.product.get'"));
            assertTrue(exception.getMessage().contains("firstCapability"));
            assertTrue(exception.getMessage().contains("secondCapability"));
        }
    }

    @Test
    void shouldRejectInvalidCapabilityDeclarations() {
        assertInvalid(InvalidNameConfig.class, "domain.resource.action");
        assertInvalid(BlankPermissionConfig.class, "permissions must contain non-blank values");
        assertInvalid(DuplicatePermissionConfig.class, "permissions must not contain duplicates");
        assertInvalid(BlankVersionConfig.class, "version is required");
        assertInvalid(InvalidTimeoutConfig.class, "timeoutMs must be greater than zero");
        assertInvalid(MultipleArgumentsConfig.class, "supports zero or one argument");
    }

    @Test
    void shouldNotRegisterScopedProxyTargetTwice() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ScopedProxyConfig.class)) {
            CapabilityRegistry registry = createRegistry(context);

            assertEquals(List.of("test.product.get", "test.product.search"),
                    registry.list().stream().map(CapabilityDefinition::getName).toList());
        }
    }

    @Test
    void shouldResolveCapabilityDeclaredOnJdkProxyInterface() throws ReflectiveOperationException {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(JdkProxyConfig.class)) {
            CapabilityRegistry registry = createRegistry(context);
            CapabilityRegistration registration = registry.getRegistration("test.product.proxy");
            ProductSearchRequest request = new ProductSearchRequest();

            Object result = registration.method().invoke(registration.bean(), request);

            assertSame(request, result);
        }
    }

    private CapabilityRegistry createRegistry(AnnotationConfigApplicationContext context) {
        return new CapabilityRegistry(context, new CapabilitySchemaGenerator());
    }

    private void assertInvalid(Class<?> configClass, String expectedMessage) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configClass)) {
            CapabilityRegistry registry = createRegistry(context);
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    registry::afterSingletonsInstantiated);
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    static class ValidConfig {
        @Bean
        ProductCapability productCapability() {
            return new ProductCapability();
        }
    }

    static class ProductCapability {

        @AgentCapability(name = "test.product.search", title = "商品搜索", description = "根据关键词搜索商品",
                category = "product", permissions = {"product:query", "product:export"},
                permissionMode = CapabilityPermissionMode.ANY, riskLevel = CapabilityRiskLevel.MEDIUM,
                confirmationRequired = true, version = "2.0.0", timeoutMs = 5_000)
        public List<ProductSearchResponse> search(ProductSearchRequest request) {
            return List.of();
        }

        @AgentCapability(name = "test.product.get", title = "商品详情", description = "查询商品详情",
                permissions = "product:query")
        public ProductSearchResponse get() {
            return new ProductSearchResponse();
        }
    }

    static class ProductSearchRequest {
        @NotBlank
        @CapabilityField(description = "商品关键词")
        private String keyword;
    }

    static class ProductSearchResponse {
        private Long id;
    }

    static class DuplicateConfig {
        @Bean("firstCapability")
        DuplicateCapability first() {
            return new DuplicateCapability();
        }

        @Bean("secondCapability")
        DuplicateCapability second() {
            return new DuplicateCapability();
        }
    }

    static class DuplicateCapability {
        @AgentCapability(name = "test.product.get", title = "商品详情", description = "查询商品详情",
                permissions = "product:query")
        public ProductSearchResponse get() {
            return new ProductSearchResponse();
        }
    }

    static class ScopedProxyConfig {
        @Bean
        @Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
        ProductCapability productCapability() {
            return new ProductCapability();
        }
    }

    static class JdkProxyConfig {
        @Bean
        ProductCapabilityApi productCapabilityApi() {
            ProxyFactory proxyFactory = new ProxyFactory(new ProductCapabilityApiImpl());
            proxyFactory.setInterfaces(ProductCapabilityApi.class);
            return (ProductCapabilityApi) proxyFactory.getProxy();
        }
    }

    interface ProductCapabilityApi {
        @AgentCapability(name = "test.product.proxy", title = "代理能力", description = "验证 JDK 代理能力",
                permissions = "product:query")
        ProductSearchRequest echo(ProductSearchRequest request);
    }

    static class ProductCapabilityApiImpl implements ProductCapabilityApi {
        @Override
        public ProductSearchRequest echo(ProductSearchRequest request) {
            return request;
        }
    }

    static class InvalidNameConfig {
        @Bean
        InvalidNameCapability invalidNameCapability() {
            return new InvalidNameCapability();
        }
    }

    static class InvalidNameCapability {
        @AgentCapability(name = "invalid_name", title = "非法名称", description = "非法名称",
                permissions = "product:query")
        public void invoke() {
        }
    }

    static class BlankPermissionConfig {
        @Bean
        BlankPermissionCapability blankPermissionCapability() {
            return new BlankPermissionCapability();
        }
    }

    static class BlankPermissionCapability {
        @AgentCapability(name = "test.product.blankpermission", title = "空权限", description = "空权限",
                permissions = " ")
        public void invoke() {
        }
    }

    static class DuplicatePermissionConfig {
        @Bean
        DuplicatePermissionCapability duplicatePermissionCapability() {
            return new DuplicatePermissionCapability();
        }
    }

    static class DuplicatePermissionCapability {
        @AgentCapability(name = "test.product.duplicatepermission", title = "重复权限", description = "重复权限",
                permissions = {"product:query", "product:query"})
        public void invoke() {
        }
    }

    static class BlankVersionConfig {
        @Bean
        BlankVersionCapability blankVersionCapability() {
            return new BlankVersionCapability();
        }
    }

    static class BlankVersionCapability {
        @AgentCapability(name = "test.product.blankversion", title = "空版本", description = "空版本",
                permissions = "product:query", version = " ")
        public void invoke() {
        }
    }

    static class InvalidTimeoutConfig {
        @Bean
        InvalidTimeoutCapability invalidTimeoutCapability() {
            return new InvalidTimeoutCapability();
        }
    }

    static class InvalidTimeoutCapability {
        @AgentCapability(name = "test.product.invalidtimeout", title = "非法超时", description = "非法超时",
                permissions = "product:query", timeoutMs = 0)
        public void invoke() {
        }
    }

    static class MultipleArgumentsConfig {
        @Bean
        MultipleArgumentsCapability multipleArgumentsCapability() {
            return new MultipleArgumentsCapability();
        }
    }

    static class MultipleArgumentsCapability {
        @AgentCapability(name = "test.product.multiplearguments", title = "多参数", description = "多参数",
                permissions = "product:query")
        public void invoke(String first, String second) {
        }
    }

}
