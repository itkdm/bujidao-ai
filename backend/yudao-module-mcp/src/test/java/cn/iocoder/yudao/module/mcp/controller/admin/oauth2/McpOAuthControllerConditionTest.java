package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationRespVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthConfiguration;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthAuthorizationService;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthClientRegistrationService;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthTokenExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpOAuthControllerConditionTest {

    @Test
    void shouldGuardOAuthEndpointsWithEnabledProperty() {
        assertEnabledCondition(McpOAuthAuthorizationController.class);
        assertEnabledCondition(McpOAuthTokenController.class);
        assertEnabledCondition(McpOAuthClientRegistrationController.class);
        // CIMD 入口 Service 与三个 Controller 用同一个开关管控
        assertEnabledCondition(McpCimdClientService.class);
    }

    @Test
    void shouldNotCreateControllerBeansWhenDisabled() {
        // 运行时断言（不是只读注解）：enabled=false 时三个 Controller Bean 都不应建出来。
        // withBean 注册时类上的 @ConditionalOnProperty 同样会被求值，Bean 直接跳过注册。
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(McpOAuthConfiguration.class))
                .withBean(McpOAuthAuthorizationController.class)
                .withBean(McpOAuthTokenController.class)
                .withBean(McpOAuthClientRegistrationController.class)
                .withPropertyValues("yudao.mcp.oauth.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(McpOAuthAuthorizationController.class);
                    assertThat(context).doesNotHaveBean(McpOAuthTokenController.class);
                    assertThat(context).doesNotHaveBean(McpOAuthClientRegistrationController.class);
                });
    }

    @Test
    void shouldCreateControllerBeansWhenEnabled() {
        // 正向对照：开关开着时三个 Controller 都应建出来，排除“注册方式本身就建不出 Bean”的空断言。
        // rateLimiter 由 McpOAuthConfiguration 提供；Service / Resolver 用 mock 补齐依赖。
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(McpOAuthConfiguration.class))
                .withBean(McpOAuthAuthorizationController.class)
                .withBean(McpOAuthTokenController.class)
                .withBean(McpOAuthClientRegistrationController.class)
                .withBean("authorizationService", McpOAuthAuthorizationService.class,
                        () -> mock(McpOAuthAuthorizationService.class))
                .withBean("tokenExchangeService", McpOAuthTokenExchangeService.class,
                        () -> mock(McpOAuthTokenExchangeService.class))
                .withBean("clientRegistrationService", McpOAuthClientRegistrationService.class,
                        () -> mock(McpOAuthClientRegistrationService.class))
                .withBean("resourceUriResolver", McpOAuthResourceUriResolver.class,
                        () -> mock(McpOAuthResourceUriResolver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpOAuthAuthorizationController.class);
                    assertThat(context).hasSingleBean(McpOAuthTokenController.class);
                    assertThat(context).hasSingleBean(McpOAuthClientRegistrationController.class);
                });
    }

    @Test
    void shouldHaveRegistrationController() {
        // DCR 已恢复：注册 Controller 类存在，且与 authorize / token 共用 enabled 开关。
        assertEnabledCondition(McpOAuthClientRegistrationController.class);
    }

    @Test
    void shouldRouteRegisterToRegistrationController() throws Exception {
        // DCR 已恢复：POST /mcp/oauth2/register 挂在注册 Controller 上，不再是 404。
        // standalone MockMvc 不需要 Spring 上下文，直接验路由本身。
        McpOAuthClientRegistrationController registrationController = new McpOAuthClientRegistrationController();
        McpOAuthClientRegistrationService registrationService = mock(McpOAuthClientRegistrationService.class);
        McpOAuthResourceUriResolver resourceUriResolver = mock(McpOAuthResourceUriResolver.class);
        when(resourceUriResolver.resolve(any())).thenReturn("http://127.0.0.1:48080/mcp");
        when(registrationService.register(any(), anyString())).thenReturn(new McpOAuthClientRegistrationRespVO()
                .setClientId("mcp-dcr-abc"));
        ReflectionTestUtils.setField(registrationController, "clientRegistrationService", registrationService);
        ReflectionTestUtils.setField(registrationController, "resourceUriResolver", resourceUriResolver);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new McpOAuthAuthorizationController(), new McpOAuthTokenController(),
                registrationController).build();

        mockMvc.perform(post("/mcp/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redirect_uris\":[\"http://127.0.0.1:10244/callback\"]}"))
                .andExpect(status().isCreated());
        // 正向对照：同前缀的现存路由确实挂着（缺参报 400 而不是 404），
        // 排除“控制器根本没注册上、所有路由都 404”的空断言。
        mockMvc.perform(post("/mcp/oauth2/token"))
                .andExpect(status().isBadRequest());
    }

    private static void assertEnabledCondition(Class<?> controllerClass) {
        ConditionalOnProperty annotation = AnnotatedElementUtils.findMergedAnnotation(
                controllerClass, ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo(McpOAuthProperties.PREFIX);
        assertThat(annotation.name()).containsExactly("enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
        assertThat(annotation.matchIfMissing()).isTrue();
    }

}
