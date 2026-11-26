package cn.iocoder.yudao.framework.mcp.config;

import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.mcp.acf.AcfMcpToolRepository;
import cn.iocoder.yudao.framework.mcp.acf.CompositeMcpSyncToolRepository;
import cn.iocoder.yudao.framework.mcp.oauth2.McpOAuthMetadataController;
import cn.iocoder.yudao.framework.mcp.security.McpAuthenticatedTransportContextExtractor;
import cn.iocoder.yudao.framework.mcp.security.McpAuthenticationEntryPoint;
import cn.iocoder.yudao.framework.mcp.security.McpQueryTokenRejectingFilter;
import cn.iocoder.yudao.framework.mcp.security.McpScopeAuthorizationFilter;
import cn.iocoder.yudao.framework.mcp.security.McpStrictAccessTokenFilter;
import cn.iocoder.yudao.framework.mcp.security.McpTenantContextFilter;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.mcp.transport.McpRequestBodyCharsetFilter;
import cn.iocoder.yudao.framework.mcp.transport.McpRequestSizeLimitFilter;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.server.HmacRequestStateCodec;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.RequestStateCodec;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
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

import java.util.Base64;
import java.util.List;

/**
 * MCP Stateless Server 自动配置。
 *
 * @author bujidao
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({McpServer.class, HttpServletMcpTransport.class})
@ConditionalOnProperty(prefix = YudaoMcpServerProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(YudaoMcpServerProperties.class)
public class YudaoMcpAutoConfiguration {

    public static final String MCP_SERVLET_NAME = "bujidaoMcpServlet";
    public static final String MCP_QUERY_TOKEN_FILTER_NAME = "bujidaoMcpQueryTokenFilter";
    public static final String MCP_REQUEST_SIZE_FILTER_NAME = "bujidaoMcpRequestSizeFilter";
    public static final String MCP_REQUEST_BODY_CHARSET_FILTER_NAME = "bujidaoMcpRequestBodyCharsetFilter";
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

    @Bean(destroyMethod = "closeGracefully")
    @ConditionalOnMissingBean(HttpServletMcpTransport.class)
    public HttpServletMcpTransport mcpServerTransport(McpServer server,
                                                      McpJsonMapper jsonMapper,
                                                      YudaoMcpServerProperties properties,
                                                      McpTransportContextExtractor<HttpServletRequest> contextExtractor,
                                                      ServerHttpHeaderValidator httpHeaderValidator) {
        return HttpServletMcpTransport.builder(server)
                .jsonMapper(jsonMapper)
                .endpoint(properties.getEndpoint())
                .contextExtractor(contextExtractor)
                .maxRequestSize(Math.toIntExact(properties.getMaxRequestSize().toBytes()))
                .httpHeaderValidator(httpHeaderValidator)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ServerHttpHeaderValidator.class)
    public ServerHttpHeaderValidator mcpServerHttpHeaderValidator(YudaoMcpServerProperties properties) {
        // DefaultServerTransportSecurityValidator 同时实现新旧两个接口，
        // 直接把它作为 ServerHttpHeaderValidator 返回，不走已废弃的 toHttpHeaderValidator 桥接，
        // Origin / Host 校验逻辑与原来一致。
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

    @Bean(name = MCP_REQUEST_BODY_CHARSET_FILTER_NAME)
    @ConditionalOnMissingBean(name = MCP_REQUEST_BODY_CHARSET_FILTER_NAME)
    public FilterRegistrationBean<McpRequestBodyCharsetFilter> mcpRequestBodyCharsetFilterRegistration(
            YudaoMcpServerProperties properties) {
        FilterRegistrationBean<McpRequestBodyCharsetFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpRequestBodyCharsetFilter());
        registration.setName(MCP_REQUEST_BODY_CHARSET_FILTER_NAME);
        registration.addUrlPatterns(properties.getEndpoint());
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(WebFilterOrderEnum.REQUEST_BODY_CACHE_FILTER + 2);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(name = MCP_SERVLET_NAME)
    public ServletRegistrationBean<HttpServletMcpTransport> mcpServletRegistration(
            HttpServletMcpTransport transport, YudaoMcpServerProperties properties) {
        ServletRegistrationBean<HttpServletMcpTransport> registration =
                new ServletRegistrationBean<>(transport, properties.getEndpoint());
        registration.setName(MCP_SERVLET_NAME);
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(RequestStateCodec.class)
    public RequestStateCodec mcpRequestStateCodec(McpJsonMapper jsonMapper,
                                                  YudaoMcpServerProperties properties) {
        // principal 绑定：transport context 里的已认证用户 id → 字符串；取不到时
        // 返回 null，state 不绑定用户（与 SDK 默认行为一致，如 STDIO 下无身份）。
        HmacRequestStateCodec.Builder codec = HmacRequestStateCodec.builder()
                .jsonMapper(jsonMapper)
                .principalExtractor(transportContext -> {
                    if (transportContext == null) {
                        return null;
                    }
                    Object userId = transportContext.get(McpTransportContextKeys.USER_ID);
                    return userId == null ? null : String.valueOf(userId);
                });
        String sharedKey = properties.getRequestState().getSharedKey();
        if (sharedKey != null && !sharedKey.isBlank()) {
            codec.key(decodeRequestStateSharedKey(sharedKey));
        }
        // 不显式设 ttl：SDK 默认 10 分钟，与确认 challenge 的 10 分钟有效期一致；
        // 单实例不配 key 时 SDK 生成随机进程 key 并打一条警告日志。
        return codec.build();
    }

    private static byte[] decodeRequestStateSharedKey(String sharedKey) {
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(sharedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "yudao.mcp.server.request-state.shared-key must be valid Base64", exception);
        }
        if (key.length < 32) {
            throw new IllegalStateException(
                    "yudao.mcp.server.request-state.shared-key must decode to at least 32 bytes");
        }
        return key;
    }

    @Bean(destroyMethod = "closeGracefully")
    @ConditionalOnMissingBean(McpServer.class)
    public McpServer mcpServer(McpJsonMapper jsonMapper,
                                YudaoMcpServerProperties properties,
                                RequestStateCodec requestStateCodec,
                                ObjectProvider<McpSyncToolRepository> toolRepositories) {
        // Modern server 没有 capabilities() / requestTimeout()：capabilities 由注册的 feature
        // 自行贡献（ToolsFeature 贡献 tools）；requestTimeout 在 2.2.0 modern Builder 里没有
        // 等价项，YudaoMcpServerProperties.requestTimeout 目前不再生效（字段已标注废弃）。
        // Resources / Prompts 不注册 feature，server/discover 不宣告，对应方法返回 METHOD_NOT_FOUND。
        // ToolsFeature 始终注册：无仓库时用空仓库，tools/list 返回 200 空列表、tools/call
        // 答 Unknown tool，与原来注册空工具列表的语义一致；多个仓库时合成一个。
        McpServer.Builder builder = McpServer.builder()
                .serverInfo(Implementation.builder(properties.getName(), properties.getVersion()).build())
                .instructions(properties.getInstructions())
                .requestStateCodec(requestStateCodec)
                .jsonMapper(jsonMapper);
        List<McpSyncToolRepository> repositories = toolRepositories.orderedStream().toList();
        McpSyncToolRepository toolRepository = repositories.isEmpty()
                ? new CompositeMcpSyncToolRepository(List.of())
                : new CompositeMcpSyncToolRepository(repositories);
        builder.feature(ToolsFeature.ofSync(toolRepository, jsonMapper,
                McpJsonDefaults.getSchemaValidator(), 0L, CacheScope.PRIVATE));
        return builder.build();
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disableServletFilterRegistration(
            T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
