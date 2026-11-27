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
import cn.iocoder.yudao.framework.mcp.tool.McpToolSpecificationProvider;
import cn.iocoder.yudao.framework.mcp.transport.McpProtocolDispatcherServlet;
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
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncListFilter;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
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

import java.time.Duration;
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
    public static final String MCP_LEGACY_SERVLET_REGISTRATION_NAME =
            "bujidaoMcpLegacyServletRegistration";
    public static final String MCP_MODERN_SERVLET_REGISTRATION_NAME =
            "bujidaoMcpModernServletRegistration";

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

    @Bean(name = MCP_MODERN_SERVLET_REGISTRATION_NAME)
    @ConditionalOnMissingBean(name = MCP_MODERN_SERVLET_REGISTRATION_NAME)
    public ServletRegistrationBean<HttpServletMcpTransport> mcpModernServletRegistration(
            HttpServletMcpTransport transport) {
        // P0-1：modern transport 同样是裸 HttpServlet bean；Spring Boot 会自动注册
        // 容器里唯一的裸 Servlet bean（单 bean 时映射为 "/"，抢走 DispatcherServlet，
        // 整个 Spring MVC 全部 404；多于一个裸 Servlet bean 时它会落在
        // /mcpServerTransport/，绕过 mcpSecurityFilterChain 与 query-token /
        // 限流过滤器）。用一个 disabled 的注册占住它，与 legacy 的
        // mcpLegacyServletRegistration 完全对称；/mcp 的注册仍归分流器
        // （mcpServletRegistration）管，分流器直接调这个 transport bean。
        ServletRegistrationBean<HttpServletMcpTransport> registration =
                new ServletRegistrationBean<>(transport);
        registration.setName(MCP_MODERN_SERVLET_REGISTRATION_NAME);
        registration.setEnabled(false);
        return registration;
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

    // P0-2：bean 名必须与 MCP_SERVLET_NAME（"bujidaoMcpServlet"）一致——测试与
    // 外部引用都按这个名取注册；只写 registration.setName() 改的是 servlet
    // 注册名，不是 Spring bean 名。补上 @Bean(name = ...) 后，
    // @ConditionalOnMissingBean(name = MCP_SERVLET_NAME) 也不再是死条件：
    // 此前同名 bean 永不存在、条件永真；现在用户自备同名注册 bean 时本方法
    // 才会被跳过（与 legacy 注册的写法对称）。
    @Bean(name = MCP_SERVLET_NAME)
    @ConditionalOnMissingBean(name = MCP_SERVLET_NAME)
    public ServletRegistrationBean<McpProtocolDispatcherServlet> mcpServletRegistration(
            HttpServletMcpTransport modernTransport,
            HttpServletStatelessServerTransport legacyTransport,
            YudaoMcpServerProperties properties, ObjectMapper objectMapper) {
        // D3：/mcp 不再直挂 modern transport，改挂分流器；注册名与 URL 映射不变，
        // 分流器按协议版本把请求转发给 modern 或 legacy transport。legacy 的
        // disabled 注册（bujidaoMcpLegacyServletRegistration）不动，legacy
        // transport 仍不产生 URL 映射。
        // D4：legacy 开关（yudao.mcp.server.legacy.enabled，默认 true）只传进分
        // 流器做入口门禁，不拆 legacy bean：开关关闭时分流到 legacy 的请求在分流
        // 器里直接被拒绝（HTTP 400 + -32022），modern 不受影响；legacy bean 仍
        // 建出来只是不被调用，不存在 bean 缺失的 NPE 分支。
        ServletRegistrationBean<McpProtocolDispatcherServlet> registration =
                new ServletRegistrationBean<>(
                        new McpProtocolDispatcherServlet(modernTransport, legacyTransport, objectMapper,
                                properties.getLegacy().isEnabled()),
                        properties.getEndpoint());
        registration.setName(MCP_SERVLET_NAME);
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    // ---- Legacy MCP 2025-11-25（stateless）----
    // D2 只建 bean，不接线；D3 由 McpProtocolDispatcherServlet 分流：legacy transport
    // 仍不注册到 /mcp 上（下面的 disabled 注册占住它），分流器直接调它的 service()。

    @Bean
    @ConditionalOnMissingBean(HttpServletStatelessServerTransport.class)
    public HttpServletStatelessServerTransport mcpLegacyStatelessServerTransport(
            McpJsonMapper jsonMapper, YudaoMcpServerProperties properties,
            McpTransportContextExtractor<HttpServletRequest> contextExtractor,
            ServerHttpHeaderValidator httpHeaderValidator) {
        // 2.2.0 里 Builder.securityValidator(...) 仍存在但已 @Deprecated，内部转调
        // ServerTransportSecurityValidator.toHttpHeaderValidator(...)；直接用
        // httpHeaderValidator(...)：DefaultServerTransportSecurityValidator 同时
        // 实现 ServerTransportSecurityValidator 与 ServerHttpHeaderValidator，
        // 这里注入的是 modern 腿已建的同一个 bean（mcpServerHttpHeaderValidator），
        // 两腿 Origin / Host 规则一致。messageEndpoint 用配置端点（默认 /mcp），
        // 与 modern 一致。maxRequestSize 不设，沿用 main 的做法（SDK 默认 16 MiB；
        // /mcp 前面的 McpRequestSizeLimitFilter 已按配置限流）。
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint(properties.getEndpoint())
                .contextExtractor(contextExtractor)
                .httpHeaderValidator(httpHeaderValidator)
                .build();
    }

    @Bean(name = MCP_LEGACY_SERVLET_REGISTRATION_NAME)
    @ConditionalOnMissingBean(name = MCP_LEGACY_SERVLET_REGISTRATION_NAME)
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpLegacyServletRegistration(
            HttpServletStatelessServerTransport transport) {
        // 关键边界：legacy transport 只以内部 bean 存在，不对外暴露。
        // HttpServletStatelessServerTransport 是 HttpServlet，Spring Boot 会自动
        // 注册容器里任何裸 Servlet bean；这里用一个 disabled 的注册占住它，避免
        // Boot 给它生成默认映射去抢 /mcp（或其它路径）。D3 的分流器直接调用该
        // transport bean，/mcp 的注册归 D3 管；modern transport 也有对称的
        // disabled 注册（mcpModernServletRegistration），同样不产生映射。
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport);
        registration.setName(MCP_LEGACY_SERVLET_REGISTRATION_NAME);
        registration.setEnabled(false);
        return registration;
    }

    @Bean(name = "mcpStatelessSyncServer", destroyMethod = "close")
    @ConditionalOnMissingBean(McpStatelessSyncServer.class)
    public McpStatelessSyncServer mcpStatelessSyncServer(
            HttpServletStatelessServerTransport transport,
            McpJsonMapper jsonMapper,
            YudaoMcpServerProperties properties,
            ObjectProvider<McpToolSpecificationProvider> toolProviders,
            ObjectProvider<McpSyncListFilter<McpSchema.Tool>> toolFilters) {
        // 与 main 的 legacy wiring 一致：serverInfo / instructions 取同一份配置
        // （modern 腿同值）；capabilities 只开 tools（listChanged 不通知）、
        // resources 空列表、prompts 不配；工具由 D1 的 McpToolSpecificationProvider
        // 喂入（目前只有 acfMcpToolSpecificationProvider）。
        // requestTimeout：main 默认 30 秒（properties 字段已随 2.2.0 迁移删除，
        // modern 腿也没有超时）；legacy 腿按 main 保留 30 秒写死在这里，而不是
        // 用 SDK 默认 10 秒。
        // 入口：McpStatelessSyncServer 构造器是包私有的，只能走
        // io.modelcontextprotocol.server.McpServer.sync(transport) 工厂
        // （与 modern 的 io.modelcontextprotocol.modern.server.McpServer 同名，
        // 这里用全限定名避免 import 冲突）。
        // tools/list 可见性：D3 用 SDK 自带的 addToolFilter 挂
        // AcfMcpLegacyToolsListFilter（ACF 自动配置提供；无可见性服务时过滤器放行
        // 全部，与 modern 仓库缺可见性服务时退回声明目录一致）。
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(false)
                .resources(false, false)
                .build();
        io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification specification =
                io.modelcontextprotocol.server.McpServer.sync(transport)
                        .jsonMapper(jsonMapper)
                        .serverInfo(properties.getName(), properties.getVersion())
                        .instructions(properties.getInstructions())
                        .requestTimeout(Duration.ofSeconds(30))
                        .capabilities(capabilities)
                        .tools(createLegacyToolSpecifications(toolProviders))
                        .resources(List.of())
                        .resourceTemplates(List.of());
        toolFilters.orderedStream().forEach(specification::addToolFilter);
        return specification.build();
    }

    private List<McpStatelessServerFeatures.SyncToolSpecification> createLegacyToolSpecifications(
            ObjectProvider<McpToolSpecificationProvider> toolProviders) {
        return toolProviders.orderedStream()
                .flatMap(provider -> provider.createToolSpecifications().stream())
                .toList();
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
        // 等价项，该配置项已随 2.2.0 迁移删除；工具调用目前无超时上限，如需超时控制要另找机制。
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
