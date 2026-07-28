package cn.iocoder.yudao.framework.mcp.security;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCall;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolInvoker;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.yudao.framework.security.config.YudaoSecurityAutoConfiguration;
import cn.iocoder.yudao.framework.security.config.YudaoWebSecurityConfigurerAdapter;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.web.TenantContextWebFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpAcfAutoConfiguration;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = McpEndpointSecurityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.application.name=mcp-security-test",
                "yudao.mcp.server.enabled=true",
                "yudao.mcp.acf.exposed-capabilities=demo.echo",
                "yudao.web.admin-ui.url=http://localhost"})
class McpEndpointSecurityIntegrationTest {

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-11-25","capabilities":{},
              "clientInfo":{"name":"security-test","version":"1.0.0"}}}
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CapabilityToolInvoker capabilityToolInvoker;

    @Autowired
    private AtomicReference<Long> invokedTenantId;

    @Test
    void shouldRejectAnonymousRequestThroughYudaoSecurityFilterChain() {
        ResponseEntity<String> response = postInitialize(new HttpHeaders());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void shouldAuthenticateBearerTokenAndPropagateIdentityToAcf() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("valid-token");

        ResponseEntity<String> initializeResponse = postInitialize(headers);
        assertThat(initializeResponse.getStatusCode().is2xxSuccessful()).isTrue();

        HttpHeaders toolHeaders = new HttpHeaders();
        toolHeaders.setBearerAuth("valid-token");
        toolHeaders.setContentType(MediaType.APPLICATION_JSON);
        toolHeaders.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        String callRequest = """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
                  "name":"demo.echo","arguments":{"message":"hello"}}}
                """;
        ResponseEntity<String> callResponse = restTemplate.postForEntity(endpoint(),
                new HttpEntity<>(callRequest, toolHeaders), String.class);

        assertThat(callResponse.getStatusCode().is2xxSuccessful()).isTrue();
        org.mockito.ArgumentCaptor<CapabilityToolCall> captor =
                org.mockito.ArgumentCaptor.forClass(CapabilityToolCall.class);
        verify(capabilityToolInvoker).invoke(captor.capture());
        assertThat(captor.getValue().getContext().getUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getContext().getTenantId()).isEqualTo(2001L);
        assertThat(captor.getValue().getContext().getConsumerId()).isEqualTo("user:1001");
        assertThat(invokedTenantId).hasValue(2001L);
    }

    @Test
    void shouldRejectTenantHeaderThatDoesNotMatchAuthenticatedUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("valid-token");
        headers.set("tenant-id", "9999");

        ResponseEntity<String> response = postInitialize(headers);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void shouldRejectAuthenticatedTokenWithoutRequiredScope() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token-without-mcp-scope");

        ResponseEntity<String> response = postInitialize(headers);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(capabilityToolInvoker, never()).invoke(any());
    }

    @Test
    void shouldRejectRequestFromDisallowedOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("valid-token");
        headers.setOrigin("https://attacker.example");

        ResponseEntity<String> response = postInitialize(headers);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(capabilityToolInvoker, never()).invoke(any());
    }

    // 非法 Host 的 421 判定在 YudaoMcpAutoConfigurationTest 中通过 Validator 直接覆盖。
    // 此处不做端到端用例：Host 属于 HttpURLConnection 受限 Header，自定义值会被静默丢弃，
    // 放开需要 JVM 级系统属性，且 Origin 用例已经证明 Validator 已接入 Transport。

    private ResponseEntity<String> postInitialize(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        return restTemplate.postForEntity(endpoint(), new HttpEntity<>(INITIALIZE_REQUEST, headers), String.class);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + port + "/mcp";
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class, YudaoSecurityAutoConfiguration.class,
            YudaoWebSecurityConfigurerAdapter.class, YudaoMcpAutoConfiguration.class, YudaoMcpAcfAutoConfiguration.class})
    static class TestApplication {

        @Bean
        WebProperties webProperties() {
            WebProperties properties = new WebProperties();
            properties.setAdminUi(new WebProperties.Ui());
            return properties;
        }

        @Bean
        WebFrameworkUtils webFrameworkUtils(WebProperties webProperties) {
            return new WebFrameworkUtils(webProperties);
        }

        @Bean
        FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilter() {
            FilterRegistrationBean<TenantContextWebFilter> registration =
                    new FilterRegistrationBean<>(new TenantContextWebFilter());
            registration.setOrder(-104);
            return registration;
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return mock(GlobalExceptionHandler.class);
        }

        @Bean
        PermissionCommonApi permissionCommonApi() {
            return mock(PermissionCommonApi.class);
        }

        @Bean
        AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
            return mock(AuthorizeRequestsCustomizer.class);
        }

        @Bean
        OAuth2TokenCommonApi oauth2TokenCommonApi() {
            OAuth2TokenCommonApi api = mock(OAuth2TokenCommonApi.class);
            OAuth2AccessTokenCheckRespDTO token = new OAuth2AccessTokenCheckRespDTO()
                    .setUserId(1001L)
                    .setTenantId(2001L)
                    .setScopes(List.of("mcp:access"))
                    .setExpiresTime(LocalDateTime.now().plusMinutes(10));
            when(api.checkAccessToken("valid-token")).thenReturn(token);
            OAuth2AccessTokenCheckRespDTO tokenWithoutScope = new OAuth2AccessTokenCheckRespDTO()
                    .setUserId(1002L)
                    .setTenantId(2001L)
                    .setScopes(List.of("user.read"))
                    .setExpiresTime(LocalDateTime.now().plusMinutes(10));
            when(api.checkAccessToken("token-without-mcp-scope")).thenReturn(tokenWithoutScope);
            return api;
        }

        @Bean
        CapabilityToolCatalog capabilityToolCatalog() {
            CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
            CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
            when(descriptor.getCapabilityName()).thenReturn("demo.echo");
            when(descriptor.getTitle()).thenReturn("Echo");
            when(descriptor.getDescription()).thenReturn("Echo input");
            when(descriptor.getInputSchema()).thenReturn(Map.of("type", "object"));
            when(descriptor.getOutputSchema()).thenReturn(Map.of("type", "string"));
            when(catalog.getDeclared("demo.echo")).thenReturn(descriptor);
            return catalog;
        }

        @Bean
        CapabilityToolInvoker capabilityToolInvoker() {
            CapabilityToolInvoker invoker = mock(CapabilityToolInvoker.class);
            when(invoker.invoke(any())).thenAnswer(invocation -> {
                invokedTenantId().set(TenantContextHolder.getTenantId());
                return CapabilityResult.success("demo.echo", (Object) "hello");
            });
            return invoker;
        }

        @Bean
        AtomicReference<Long> invokedTenantId() {
            return new AtomicReference<>();
        }

    }

}