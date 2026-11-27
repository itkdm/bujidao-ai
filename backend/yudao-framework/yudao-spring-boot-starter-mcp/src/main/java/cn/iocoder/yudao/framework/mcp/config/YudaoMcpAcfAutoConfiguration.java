package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolCallHandler;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolMapper;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolRepository;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolSpecificationProvider;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpStructuredContentNormalizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MCP ACF Tool Provider 自动配置。
 *
 * @author bujidao
 */
@AutoConfiguration(after = YudaoMcpAutoConfiguration.class)
@ConditionalOnClass(name = {
        "cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog",
        "io.modelcontextprotocol.modern.server.feature.ToolsFeature"
})
@ConditionalOnProperty(prefix = YudaoMcpServerProperties.PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = YudaoMcpAcfProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean({CapabilityToolCatalog.class, CapabilityToolInvoker.class})
@EnableConfigurationProperties(YudaoMcpAcfProperties.class)
public class YudaoMcpAcfAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CapabilityToolContractSupport.class)
    public CapabilityToolContractSupport capabilityToolContractSupport() {
        return new CapabilityToolContractSupport();
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolMapper acfMcpToolMapper(CapabilityToolContractSupport toolContractSupport) {
        return new AcfMcpToolMapper(toolContractSupport);
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpStructuredContentNormalizer acfMcpStructuredContentNormalizer() {
        return new AcfMcpStructuredContentNormalizer();
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolCallHandler acfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker,
                                                       McpJsonMapper jsonMapper,
                                                       AcfMcpStructuredContentNormalizer structuredContentNormalizer,
                                                       CapabilityToolContractSupport toolContractSupport,
                                                       ObjectProvider<CapabilityConfirmationService> confirmationService) {
        // 确认服务用 ObjectProvider 取：模块未接确认服务实现时 handler 仍能启动，
        // 只是需要确认的调用会回明确的“确认服务未配置”错误，不走 elicitation。
        return new AcfMcpToolCallHandler(capabilityToolInvoker, jsonMapper, structuredContentNormalizer,
                toolContractSupport, confirmationService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolRepository acfMcpToolRepository(
            CapabilityToolCatalog capabilityToolCatalog, AcfMcpToolMapper toolMapper,
            AcfMcpToolCallHandler toolCallHandler,
            ObjectProvider<CapabilityToolExportService> toolExportService) {
        // toolExportService 用 ObjectProvider 取：缺失时 repository 退回声明目录，
        // 而不是让整个 ACF 自动配置启动失败。
        return new AcfMcpToolRepository(capabilityToolCatalog, toolMapper, toolCallHandler,
                toolExportService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolSpecificationProvider acfMcpToolSpecificationProvider(
            CapabilityToolCatalog capabilityToolCatalog, AcfMcpToolMapper toolMapper,
            AcfMcpToolCallHandler toolCallHandler) {
        // 只产出 legacy（2025-11-25）规格列表，不建服务端：legacy 服务端与 transport
        // 已由 YudaoMcpAutoConfiguration 建好（D2），路由/分流由后续块（D3）接线。
        return new AcfMcpToolSpecificationProvider(capabilityToolCatalog, toolMapper, toolCallHandler);
    }

}
