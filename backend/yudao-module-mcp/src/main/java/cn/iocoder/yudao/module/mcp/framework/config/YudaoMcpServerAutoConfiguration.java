package cn.iocoder.yudao.module.mcp.framework.config;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.module.mcp.framework.security.McpAuthenticatedTransportContextExtractor;
import cn.iocoder.yudao.module.mcp.framework.security.McpAuthenticationEntryPoint;
import cn.iocoder.yudao.module.mcp.framework.security.McpQueryTokenRejectingFilter;
import cn.iocoder.yudao.module.mcp.framework.security.McpTenantContextFilter;
import cn.iocoder.yudao.module.mcp.framework.tool.AcfMcpToolCallHandler;
import cn.iocoder.yudao.module.mcp.framework.tool.AcfMcpToolSpecificationFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
    public static final String MCP_QUERY_TOKEN_FILTER_NAME = "bujidaoMcpQueryTokenFilter";

    @Bean
    @ConditionalOnMissingBean(McpJsonMapper.class)
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(HttpServletStatelessServerTransport.class)
    public HttpServletStatelessServerTransport mcpStatelessServerTransport(
            McpJsonMapper jsonMapper, YudaoMcpServerProperties properties,
            McpTransportContextExtractor<HttpServletRequest> contextExtractor,
            ServerTransportSecurityValidator securityValidator) {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint(properties.getEndpoint())
                .contextExtractor(contextExtractor)
                .securityValidator(securityValidator)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ServerTransportSecurityValidator.class)
    public ServerTransportSecurityValidator mcpServerTransportSecurityValidator(
            YudaoMcpServerProperties properties) {
        return DefaultServerTransportSecurityValidator.builder()
                .allowedOrigins(properties.getAllowedOrigins())
                .allowedHosts(properties.getAllowedHosts())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(McpTransportContextExtractor.class)
    public McpTransportContextExtractor<HttpServletRequest> mcpTransportContextExtractor() {
        return new McpAuthenticatedTransportContextExtractor();
    }

    @Bean("mcpSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean(name = "mcpSecurityFilterChain")
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http,
                                                      YudaoMcpServerProperties properties,
                                                      TokenAuthenticationFilter tokenAuthenticationFilter,
                                                      McpTenantContextFilter tenantContextFilter)
            throws Exception {
        http.securityMatcher(properties.getEndpoint())
                .authorizeHttpRequests(registry -> registry.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(configurer -> configurer
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint(new McpAuthenticationEntryPoint()))
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, TokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpTenantContextFilter mcpTenantContextFilter() {
        return new McpTenantContextFilter();
    }

    @Bean(name = MCP_QUERY_TOKEN_FILTER_NAME)
    @ConditionalOnMissingBean(name = MCP_QUERY_TOKEN_FILTER_NAME)
    public FilterRegistrationBean<McpQueryTokenRejectingFilter> mcpQueryTokenFilterRegistration(
            YudaoMcpServerProperties properties, SecurityProperties securityProperties) {
        FilterRegistrationBean<McpQueryTokenRejectingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpQueryTokenRejectingFilter(securityProperties.getTokenParameter()));
        registration.setName(MCP_QUERY_TOKEN_FILTER_NAME);
        registration.addUrlPatterns(properties.getEndpoint());
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
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
    public AcfMcpToolCallHandler acfMcpToolCallHandler(CapabilityToolInvoker capabilityToolInvoker,
                                                       McpJsonMapper jsonMapper) {
        return new AcfMcpToolCallHandler(capabilityToolInvoker, jsonMapper);
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
