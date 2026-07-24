package cn.iocoder.yudao.module.mcp.framework.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.module.mcp.framework.tool.AcfMcpToolCallHandler;
import cn.iocoder.yudao.module.mcp.framework.tool.AcfMcpToolSpecificationFactory;
import cn.iocoder.yudao.module.mcp.framework.security.McpAuthenticatedTransportContextExtractor;
import cn.iocoder.yudao.module.mcp.framework.security.McpAuthorizeRequestsCustomizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * MCP Stateless Server 自动配置
 *
 * @author bujidao
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({McpServer.class, HttpServletStatelessServerTransport.class})
@ConditionalOnProperty(prefix = YudaoMcpServerProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties({YudaoMcpServerProperties.class, YudaoMcpToolProperties.class})
public class YudaoMcpServerAutoConfiguration {

    public static final String MCP_SERVLET_NAME = "bujidaoMcpServlet";

    @Bean
    @ConditionalOnMissingBean(McpJsonMapper.class)
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(HttpServletStatelessServerTransport.class)
    public HttpServletStatelessServerTransport mcpStatelessServerTransport(
            McpJsonMapper jsonMapper, YudaoMcpServerProperties properties,
            McpTransportContextExtractor<HttpServletRequest> contextExtractor) {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint(properties.getEndpoint())
                .contextExtractor(contextExtractor)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(McpTransportContextExtractor.class)
    public McpTransportContextExtractor<HttpServletRequest> mcpTransportContextExtractor() {
        return new McpAuthenticatedTransportContextExtractor();
    }

    @Bean("mcpAuthorizeRequestsCustomizer")
    @ConditionalOnMissingBean(name = "mcpAuthorizeRequestsCustomizer")
    public McpAuthorizeRequestsCustomizer mcpAuthorizeRequestsCustomizer(YudaoMcpServerProperties properties) {
        return new McpAuthorizeRequestsCustomizer(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = MCP_SERVLET_NAME)
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServletRegistration(
            HttpServletStatelessServerTransport transport, YudaoMcpServerProperties properties) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport, properties.getEndpoint());
        registration.setName(MCP_SERVLET_NAME);
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(McpStatelessSyncServer.class)
    public McpStatelessSyncServer mcpStatelessSyncServer(HttpServletStatelessServerTransport transport,
                                                         McpJsonMapper jsonMapper,
                                                         YudaoMcpServerProperties properties,
                                                         List<McpStatelessServerFeatures.SyncToolSpecification> tools) {
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(false)
                .build();
        return McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .serverInfo(properties.getName(), properties.getVersion())
                .instructions(properties.getInstructions())
                .requestTimeout(properties.getRequestTimeout())
                .capabilities(capabilities)
                .tools(tools)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolCallHandler acfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker) {
        return new AcfMcpToolCallHandler(capabilityToolInvoker);
    }

    @Bean
    @ConditionalOnMissingBean
    public AcfMcpToolSpecificationFactory acfMcpToolSpecificationFactory(
            CapabilityToolCatalog capabilityToolCatalog, YudaoMcpToolProperties properties,
            AcfMcpToolCallHandler toolCallHandler) {
        return new AcfMcpToolSpecificationFactory(capabilityToolCatalog, properties, toolCallHandler);
    }

    @Bean
    public List<McpStatelessServerFeatures.SyncToolSpecification> mcpToolSpecifications(
            AcfMcpToolSpecificationFactory factory) {
        return factory.createToolSpecifications();
    }

}
