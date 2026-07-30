package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.acf.config.YudaoAcfProperties;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpConfirmationTool;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolMapper;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolCallHandler;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolSpecificationProvider;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpStructuredContentNormalizer;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolsListFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        "io.modelcontextprotocol.server.McpStatelessServerFeatures"
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
                                                       CapabilityToolContractSupport toolContractSupport) {
        return new AcfMcpToolCallHandler(capabilityToolInvoker, jsonMapper, structuredContentNormalizer,
                toolContractSupport);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CapabilityConfirmationService.class)
    @ConditionalOnProperty(prefix = YudaoAcfProperties.PREFIX + ".confirmation",
            name = "enabled", havingValue = "true")
    public AcfMcpConfirmationTool acfMcpConfirmationTool(CapabilityConfirmationService confirmationService) {
        return new AcfMcpConfirmationTool(confirmationService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolSpecificationProvider acfMcpToolSpecificationProvider(
            CapabilityToolCatalog capabilityToolCatalog, AcfMcpToolMapper toolMapper,
            AcfMcpToolCallHandler toolCallHandler, ObjectProvider<AcfMcpConfirmationTool> confirmationTool) {
        return new AcfMcpToolSpecificationProvider(capabilityToolCatalog, toolMapper, toolCallHandler,
                confirmationTool.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CapabilityToolExportService.class)
    public AcfMcpToolsListFilter acfMcpToolsListFilter(CapabilityToolExportService toolExportService,
                                                       AcfMcpToolMapper toolMapper,
                                                       ObjectMapper objectMapper,
                                                       ObjectProvider<AcfMcpConfirmationTool> confirmationTool) {
        return new AcfMcpToolsListFilter(toolExportService, toolMapper, objectMapper,
                confirmationTool.getIfAvailable());
    }

}
