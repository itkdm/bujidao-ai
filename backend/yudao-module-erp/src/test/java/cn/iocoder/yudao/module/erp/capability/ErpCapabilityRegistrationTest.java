package cn.iocoder.yudao.module.erp.capability;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.schema.CapabilitySchemaGenerator;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityRegistry;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerCapabilityDTO;
import cn.iocoder.yudao.module.erp.service.product.ErpProductService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.yudao.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockService;
import cn.iocoder.yudao.module.erp.service.stock.ErpWarehouseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 校验 ERP 能力能通过 ACF 真实的注册与 Schema 生成流程
 *
 * 这里不是重复测试 {@link ErpCapabilityProvider} 的业务逻辑，而是守住启动期约束：
 * 能力命名、权限声明、请求 DTO 的 Schema 必须能被 ACF 内核接受，避免应用起不来。
 *
 * @author bujidao
 */
class ErpCapabilityRegistrationTest {

    /** 与 yudao-server 的 yudao.mcp.tools.exposed-capabilities 白名单保持一致 */
    private static final List<String> EXPOSED_CAPABILITIES = List.of(
            "erp.customer.search",
            "erp.product.search",
            "erp.stock.query",
            "erp.supplier.search",
            "erp.warehouse.search");

    @Test
    void shouldRegisterAllErpCapabilities() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            CapabilityRegistry registry = context.getBean(CapabilityRegistry.class);

            List<String> names = registry.list().stream().map(CapabilityDefinition::getName).toList();
            assertThat(names).containsExactlyElementsOf(EXPOSED_CAPABILITIES);
        }
    }

    @Test
    void shouldDeclareReadOnlyLowRiskCapabilities() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            CapabilityRegistry registry = context.getBean(CapabilityRegistry.class);

            for (CapabilityDefinition definition : registry.list()) {
                assertThat(definition.isSideEffect()).as("%s 应为只读能力", definition.getName()).isFalse();
                assertThat(definition.isConfirmationRequired())
                        .as("%s 不应要求人工确认", definition.getName()).isFalse();
                assertThat(definition.getRiskLevel()).isEqualTo(CapabilityRiskLevel.LOW);
                assertThat(definition.getCategory()).isEqualTo("ERP");
                assertThat(definition.getPermissions()).isNotEmpty();
            }
        }
    }

    @Test
    void shouldRequireBothStockAndWarehousePermissionForStockQuery() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            CapabilityDefinition definition = context.getBean(CapabilityRegistry.class).get("erp.stock.query");

            // 结果里带仓库名称，因此必须同时具备两个查询权限，避免绕过仓库权限读到仓库档案
            assertThat(definition.getPermissions()).containsExactly("erp:stock:query", "erp:warehouse:query");
            assertThat(definition.getPermissionMode()).isEqualTo(CapabilityPermissionMode.ALL);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateInputSchemaForSearchCapability() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            CapabilityDefinition definition = context.getBean(CapabilityRegistry.class).get("erp.product.search");

            Map<String, Object> schema = definition.getInputSchema();
            assertThat(schema).containsEntry("type", "object");
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertThat(properties).containsKeys("keyword", "categoryId", "pageNo", "pageSize");
            Map<String, Object> pageSize = (Map<String, Object>) properties.get("pageSize");
            assertThat(pageSize).containsEntry("type", "integer")
                    .containsEntry("minimum", 1L)
                    .containsEntry("maximum", 50L);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkContactFieldsAsSensitiveAndOmitFinancialFields() {
        Map<String, Object> schema = new CapabilitySchemaGenerator().generate(ErpCustomerCapabilityDTO.class);

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("id", "name", "contact", "mobile", "telephone", "status");
        // 财务敏感字段不进入能力视图，Agent 无法读取
        assertThat(properties).doesNotContainKeys("taxNo", "taxPercent", "bankName", "bankAccount", "bankAddress");
        assertThat((Map<String, Object>) properties.get("mobile")).containsEntry("x-sensitive", true);
        assertThat((Map<String, Object>) properties.get("telephone")).containsEntry("x-sensitive", true);
    }

    private AnnotationConfigApplicationContext createContext() {
        return new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CapabilitySchemaGenerator capabilitySchemaGenerator() {
            return new CapabilitySchemaGenerator();
        }

        @Bean
        CapabilityRegistry capabilityRegistry(AnnotationConfigApplicationContext context,
                                              CapabilitySchemaGenerator schemaGenerator) {
            return new CapabilityRegistry(context, schemaGenerator);
        }

        @Bean
        ErpCapabilityProvider erpCapabilityProvider() {
            return new ErpCapabilityProvider();
        }

        @Bean
        ErpProductService erpProductService() {
            return mock(ErpProductService.class);
        }

        @Bean
        ErpStockService erpStockService() {
            return mock(ErpStockService.class);
        }

        @Bean
        ErpWarehouseService erpWarehouseService() {
            return mock(ErpWarehouseService.class);
        }

        @Bean
        ErpCustomerService erpCustomerService() {
            return mock(ErpCustomerService.class);
        }

        @Bean
        ErpSupplierService erpSupplierService() {
            return mock(ErpSupplierService.class);
        }

    }

}
