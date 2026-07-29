package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.mcp.oauth2.McpOAuthMetadataController;
import cn.iocoder.yudao.framework.mcp.security.McpAuthenticatedTransportContextExtractor;
import cn.iocoder.yudao.framework.mcp.security.McpAuthenticationEntryPoint;
import cn.iocoder.yudao.framework.mcp.security.McpQueryTokenRejectingFilter;
import cn.iocoder.yudao.framework.mcp.security.McpScopeAuthorizationFilter;
import cn.iocoder.yudao.framework.mcp.security.McpStrictAccessTokenFilter;
import cn.iocoder.yudao.framework.mcp.security.McpTenantContextFilter;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.mcp.tool.McpToolSpecificationProvider;
import cn.iocoder.yudao.framework.mcp.transport.McpRequestSizeLimitFilter;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
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
 * MCP Stateless Server 自动配置。
 *
 * @author bujidao
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({McpServer.class, HttpServletStatelessServerTransport.class})
@ConditionalOnProperty(prefix = YudaoMcpServerProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(YudaoMcpServerProperties.class)
public class YudaoMcpAutoConfiguration {

    public static final String MCP_SERVLET_NAME = "bujidaoMcpServlet";
    public static final String MCP_QUERY_TOKEN_FILTER_NAME = "bujidaoMcpQueryTokenFilter";
    public static final String MCP_REQUEST_SIZE_FILTER_NAME = "bujidaoMcpRequestSizeFilter";
    public static final String MCP_STRICT_ACCESS_TOKEN_FILTER_REGISTRATION_NAME =
            "bujidaoMcpStrictAccessTokenFilterRegistration";
    public static final String MCP_SCOPE_AUTHORIZATION_FILTER_REGISTRATION_NAME =
            "bujidaoMcpScopeAuthorizationFilterRegistration";
    public static final String MCP_TENANT_CONTEXT_FILTER_REGISTRATION_NAME =
            "bujidaoMcpTenantContextFilterRegistration";

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
                                                      McpStrictAccessTokenFilter strictAccessTokenFilter,
                                                      McpScopeAuthorizationFilter scopeAuthorizationFilter,
                                                      McpTenantContextFilter tenantContextFilter)
            throws Exception {
        // 过滤器顺序：认证 -> scope 判定 -> 租户上下文收口，避免为无权限请求绑定租户上下文
        http.securityMatcher(properties.getEndpoint())
                .authorizeHttpRequests(registry -> registry.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(configurer -> configurer
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint(new McpAuthenticationEntryPoint(properties)))
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(strictAccessTokenFilter, TokenAuthenticationFilter.class)
                .addFilterAfter(scopeAuthorizationFilter, McpStrictAccessTokenFilter.class)
                .addFilterAfter(tenantContextFilter, McpScopeAuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpStrictAccessTokenFilter mcpStrictAccessTokenFilter(
            YudaoMcpServerProperties properties, SecurityProperties securityProperties,
            OAuth2TokenCommonApi oauth2TokenApi) {
        return new McpStrictAccessTokenFilter(properties, securityProperties, oauth2TokenApi);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpScopeAuthorizationFilter mcpScopeAuthorizationFilter(YudaoMcpServerProperties properties) {
        return new McpScopeAuthorizationFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpOAuthMetadataController mcpOAuthMetadataController(
            YudaoMcpServerProperties properties, ObjectProvider<WebProperties> webProperties) {
        return new McpOAuthMetadataController(properties, webProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpTenantContextFilter mcpTenantContextFilter() {
        return new McpTenantContextFilter();
    }

    @Bean(name = MCP_STRICT_ACCESS_TOKEN_FILTER_REGISTRATION_NAME)
    @ConditionalOnMissingBean(name = MCP_STRICT_ACCESS_TOKEN_FILTER_REGISTRATION_NAME)
    public FilterRegistrationBean<McpStrictAccessTokenFilter> mcpStrictAccessTokenFilterRegistration(
            McpStrictAccessTokenFilter filter) {
        return disableServletFilterRegistration(filter);
    }

    @Bean(name = MCP_SCOPE_AUTHORIZATION_FILTER_REGISTRATION_NAME)
    @ConditionalOnMissingBean(name = MCP_SCOPE_AUTHORIZATION_FILTER_REGISTRATION_NAME)
    public FilterRegistrationBean<McpScopeAuthorizationFilter> mcpScopeAuthorizationFilterRegistration(
            McpScopeAuthorizationFilter filter) {
        return disableServletFilterRegistration(filter);
    }

    @Bean(name = MCP_TENANT_CONTEXT_FILTER_REGISTRATION_NAME)
    @ConditionalOnMissingBean(name = MCP_TENANT_CONTEXT_FILTER_REGISTRATION_NAME)
    public FilterRegistrationBean<McpTenantContextFilter> mcpTenantContextFilterRegistration(
            McpTenantContextFilter filter) {
        return disableServletFilterRegistration(filter);
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

    @Bean(name = MCP_REQUEST_SIZE_FILTER_NAME)
    @ConditionalOnMissingBean(name = MCP_REQUEST_SIZE_FILTER_NAME)
    public FilterRegistrationBean<McpRequestSizeLimitFilter> mcpRequestSizeFilterRegistration(
            YudaoMcpServerProperties properties) {
        FilterRegistrationBean<McpRequestSizeLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpRequestSizeLimitFilter(properties.getMaxRequestSize().toBytes()));
        registration.setName(MCP_REQUEST_SIZE_FILTER_NAME);
        registration.addUrlPatterns(properties.getEndpoint());
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
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
                                                         ObjectProvider<McpToolSpecificationProvider> toolProviders) {
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(false)
                .resources(false, false)
                .build();
        return McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .serverInfo(properties.getName(), properties.getVersion())
                .instructions(properties.getInstructions())
                .requestTimeout(properties.getRequestTimeout())
                .capabilities(capabilities)
                .tools(createToolSpecifications(toolProviders))
                .resources(List.of())
                .resourceTemplates(List.of())
                .build();
    }

    private List<McpStatelessServerFeatures.SyncToolSpecification> createToolSpecifications(
            ObjectProvider<McpToolSpecificationProvider> toolProviders) {
        return toolProviders.orderedStream()
                .flatMap(provider -> provider.createToolSpecifications().stream())
                .toList();
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disableServletFilterRegistration(
            T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
