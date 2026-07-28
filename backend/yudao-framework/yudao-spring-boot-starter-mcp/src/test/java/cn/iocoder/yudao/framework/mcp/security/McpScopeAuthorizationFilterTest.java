package cn.iocoder.yudao.framework.mcp.security;

import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class McpScopeAuthorizationFilterTest {

    private final McpScopeAuthorizationFilter filter =
            new McpScopeAuthorizationFilter(properties(List.of("mcp:access")));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenRequiredScopeGranted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request, List.of("mcp:access", "user.read"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void shouldRejectAuthenticatedUserWithoutRequiredScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request, List.of("user.read"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getHeader("WWW-Authenticate"))
                .contains("error=\"insufficient_scope\"", "scope=\"mcp:access\"",
                        "resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\"");
    }

    @Test
    void shouldRejectAuthenticatedUserWithoutAnyScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request, List.of());

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldRejectAuthenticatedUserWithNullScopes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request, null);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldRequireAllConfiguredScopes() throws Exception {
        McpScopeAuthorizationFilter multiScopeFilter =
                new McpScopeAuthorizationFilter(properties(List.of("mcp:access", "mcp:tools")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request, List.of("mcp:access"));

        multiScopeFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldDeferUnauthenticatedRequestToAuthenticationEntryPoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // 未认证请求不由本过滤器判定，保持 401 语义
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void shouldIgnoreScopeCarriedByRequestParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("scope", "mcp:access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request, List.of("user.read"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    private static void login(MockHttpServletRequest request, List<String> scopes) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(1001L);
        loginUser.setTenantId(2001L);
        loginUser.setScopes(scopes);
        SecurityFrameworkUtils.setLoginUser(loginUser, request);
    }

    private static YudaoMcpServerProperties properties(List<String> requiredScopes) {
        YudaoMcpServerProperties properties = new YudaoMcpServerProperties();
        properties.setRequiredScopes(requiredScopes);
        return properties;
    }

}
