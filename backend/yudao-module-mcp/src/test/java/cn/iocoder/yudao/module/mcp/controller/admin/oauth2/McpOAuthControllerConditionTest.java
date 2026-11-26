package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpOAuthControllerConditionTest {

    @Test
    void shouldGuardOAuthEndpointsWithEnabledProperty() {
        assertEnabledCondition(McpOAuthAuthorizationController.class);
        assertEnabledCondition(McpOAuthTokenController.class);
        // CIMD 入口 Service 与两个 Controller 用同一个开关管控
        assertEnabledCondition(McpCimdClientService.class);
    }

    @Test
    void shouldNotHaveRegistrationController() {
        // DCR 已移除：注册 Controller 类本身不应存在，POST /mcp/oauth2/register
        // 因此无路由可挂。路由缺失由“类不存在 + 下面的 MockMvc 404 + 元数据无
        // registration_endpoint（见 McpOAuthMetadataControllerTest）”共同锁定。
        assertThatThrownBy(() -> Class.forName(
                "cn.iocoder.yudao.module.mcp.controller.admin.oauth2.McpOAuthClientRegistrationController"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void shouldReturn404ForRegisterRoute() throws Exception {
        // DCR 已移除：/mcp/oauth2 下只剩 authorize / token 两个 Controller，
        // POST /mcp/oauth2/register 无任何 handler。standalone MockMvc 不需要
        // Spring 上下文，直接验路由本身，而不是只验“注册 Controller 类不存在”。
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new McpOAuthAuthorizationController(), new McpOAuthTokenController()).build();

        mockMvc.perform(post("/mcp/oauth2/register"))
                .andExpect(status().isNotFound());
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
