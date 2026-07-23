package cn.iocoder.yudao.framework.acf.core.service;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityStatus;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityInvokeCommand;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityExecutorTest {

    private AnnotationConfigApplicationContext applicationContext;
    private ValidatorFactory validatorFactory;
    private CapabilityExecutor executor;
    private ProductCapability productCapability;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext();
        productCapability = new ProductCapability();
        applicationContext.registerBean("productCapability", ProductCapability.class, () -> productCapability);
        applicationContext.registerBean("proxyCapability", ProxyCapabilityApi.class, this::createProxyCapability);
        applicationContext.refresh();

        validatorFactory = Validation.buildDefaultValidatorFactory();
        CapabilityRegistry registry = new CapabilityRegistry(applicationContext, new CapabilitySchemaGenerator());
        executor = new CapabilityExecutor(registry, new ObjectMapper(), validatorFactory.getValidator());
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
        applicationContext.close();
    }

    @Test
    void shouldConvertValidateAndInvokeCapability() {
        CapabilityResult result = invoke("test.product.search", Map.of("keyword", "keyboard", "limit", 5));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(CapabilityStatus.SUCCESS);
        assertThat(result.getData()).isEqualTo(new ProductResponse("keyboard", 5));
        assertThat(productCapability.invocationCount).isEqualTo(1);
    }

    @Test
    void shouldPreserveGenericArgumentTypeDuringConversion() {
        CapabilityResult result = invoke("test.product.batch", List.of(
                Map.of("keyword", "keyboard", "limit", 1),
                Map.of("keyword", "mouse", "limit", 2)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(List.of("keyboard", "mouse"));
    }

    @Test
    void shouldRejectInvalidArgumentBeforeInvokingTarget() {
        CapabilityResult result = invoke("test.product.search", Map.of("keyword", "", "limit", 0));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_BAD_REQUEST);
        assertThat(result.getMessage()).startsWith("keyword ");
        assertThat(productCapability.invocationCount).isZero();
    }

    @Test
    void shouldInvokeCapabilityWithoutArgument() {
        CapabilityResult result = invoke("test.product.ping", Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("pong");
    }

    @Test
    void shouldRejectUnexpectedArgumentForNoArgumentCapability() {
        CapabilityResult result = invoke("test.product.ping", Map.of("unexpected", true));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_BAD_REQUEST);
        assertThat(result.getMessage()).contains("does not accept arguments");
    }

    @Test
    void shouldReturnFailureWhenCapabilityDoesNotExist() {
        CapabilityResult result = invoke("test.product.missing", Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_CAPABILITY_NOT_FOUND);
    }

    @Test
    void shouldReturnFailureWhenTargetThrowsException() {
        CapabilityResult result = invoke("test.product.fail", Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(CapabilityExecutor.ERROR_INVOKE);
        assertThat(result.getMessage()).isEqualTo("inventory unavailable");
    }

    @Test
    void shouldInvokeJdkProxyCapability() {
        CapabilityResult result = invoke("test.product.proxy", "hello");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("HELLO");
    }

    private CapabilityResult invoke(String name, Object arguments) {
        return executor.invoke(CapabilityInvokeCommand.builder()
                .name(name)
                .arguments(arguments)
                .build());
    }

    private ProxyCapabilityApi createProxyCapability() {
        ProxyFactory proxyFactory = new ProxyFactory(new ProxyCapabilityApiImpl());
        proxyFactory.setInterfaces(ProxyCapabilityApi.class);
        return (ProxyCapabilityApi) proxyFactory.getProxy();
    }

    static class ProductCapability {

        private int invocationCount;

        @AgentCapability(name = "test.product.search", title = "搜索商品", description = "按关键词搜索商品",
                permissions = "product:query")
        public ProductResponse search(ProductRequest request) {
            invocationCount++;
            return new ProductResponse(request.keyword(), request.limit());
        }

        @AgentCapability(name = "test.product.batch", title = "批量搜索商品", description = "批量转换泛型参数",
                permissions = "product:query")
        public List<String> batch(List<ProductRequest> requests) {
            return requests.stream().map(ProductRequest::keyword).toList();
        }

        @AgentCapability(name = "test.product.ping", title = "检查商品能力", description = "验证无参数调用",
                permissions = "product:query")
        public String ping() {
            return "pong";
        }

        @AgentCapability(name = "test.product.fail", title = "失败商品能力", description = "验证业务异常转换",
                permissions = "product:query")
        public void fail() {
            throw new IllegalStateException("inventory unavailable");
        }
    }

    record ProductRequest(@NotBlank String keyword, @Min(1) int limit) {
    }

    record ProductResponse(String keyword, int limit) {
    }

    interface ProxyCapabilityApi {

        @AgentCapability(name = "test.product.proxy", title = "代理商品能力", description = "验证代理方法执行",
                permissions = "product:query")
        String invoke(String value);
    }

    static class ProxyCapabilityApiImpl implements ProxyCapabilityApi {

        @Override
        public String invoke(String value) {
            return value.toUpperCase();
        }
    }

}
