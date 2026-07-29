package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class McpOAuthControllerConditionTest {

    @Test
    void shouldGuardOAuthEndpointsWithEnabledProperty() {
        assertEnabledCondition(McpOAuthAuthorizationController.class);
        assertEnabledCondition(McpOAuthClientRegistrationController.class);
        assertEnabledCondition(McpOAuthTokenController.class);
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
