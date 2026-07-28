package cn.iocoder.yudao.framework.mcp.security;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
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
import static org.mockito.Mockito.when;

class McpStrictAccessTokenFilterTest {

    private final OAuth2TokenCommonApi oauth2TokenApi = mock(OAuth2TokenCommonApi.class);
    private final McpStrictAccessTokenFilter filter = new McpStrictAccessTokenFilter(
            new YudaoMcpServerProperties(), new SecurityProperties(), oauth2TokenApi);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughAccessTokenAndExposeClientId() throws Exception {
        MockHttpServletRequest request = request("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        LoginUser loginUser = login(request);
        when(oauth2TokenApi.checkAccessToken("access-token")).thenReturn(token(
                "access-token", "refresh-token", "workbuddy-mcp"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(loginUser.getContext(McpTransportContextKeys.CLIENT_ID, String.class))
                .isEqualTo("workbuddy-mcp");
    }

    @Test
    void shouldRejectRefreshTokenFallback() throws Exception {
        MockHttpServletRequest request = request("refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request);
        when(oauth2TokenApi.checkAccessToken("refresh-token")).thenReturn(token(
                "refresh-token", "refresh-token", "workbuddy-mcp"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate"))
                .contains("resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\"");
    }

    @Test
    void shouldRejectTokenWithoutAccessTokenEcho() throws Exception {
        MockHttpServletRequest request = request("unknown-token-shape");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        login(request);
        when(oauth2TokenApi.checkAccessToken("unknown-token-shape")).thenReturn(new OAuth2AccessTokenCheckRespDTO()
                .setClientId("workbuddy-mcp")
                .setScopes(List.of("mcp:access")));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldDeferUnauthenticatedRequestToAuthenticationEntryPoint() throws Exception {
        MockHttpServletRequest request = request("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    private static OAuth2AccessTokenCheckRespDTO token(String accessToken, String refreshToken, String clientId) {
        return new OAuth2AccessTokenCheckRespDTO()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setClientId(clientId)
                .setScopes(List.of("mcp:access"));
    }

    private static LoginUser login(MockHttpServletRequest request) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(1001L);
        loginUser.setTenantId(2001L);
        loginUser.setScopes(List.of("mcp:access"));
        SecurityFrameworkUtils.setLoginUser(loginUser, request);
        return loginUser;
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

}
