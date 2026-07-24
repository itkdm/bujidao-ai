package cn.iocoder.yudao.module.mcp.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class McpAuthenticatedTransportContextExtractorTest {

    private final McpAuthenticatedTransportContextExtractor extractor =
            new McpAuthenticatedTransportContextExtractor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExtractTrustedLoginUserContext() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(10L);
        loginUser.setTenantId(20L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        SecurityFrameworkUtils.setLoginUser(loginUser, request);

        McpTransportContext context = extractor.extract(request);

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isEqualTo(10L);
        assertThat(context.get(McpTransportContextKeys.TENANT_ID)).isEqualTo(20L);
        assertThat(context.get(McpTransportContextKeys.CONSUMER_ID)).isEqualTo("user:10");
    }

    @Test
    void shouldPreferVisitedTenantForCrossTenantAccess() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(10L);
        loginUser.setTenantId(20L);
        loginUser.setVisitTenantId(30L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        SecurityFrameworkUtils.setLoginUser(loginUser, request);

        McpTransportContext context = extractor.extract(request);

        assertThat(context.get(McpTransportContextKeys.TENANT_ID)).isEqualTo(30L);
    }

    @Test
    void shouldFailClosedWithoutYudaoLoginUser() {
        assertThatThrownBy(() -> extractor.extract(mock(HttpServletRequest.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated LoginUser is required for MCP requests");
    }

}
