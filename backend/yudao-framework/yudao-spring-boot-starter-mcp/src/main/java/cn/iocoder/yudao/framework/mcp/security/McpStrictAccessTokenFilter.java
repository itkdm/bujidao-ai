package cn.iocoder.yudao.framework.mcp.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MCP 专用 access token 类型校验。
 *
 * @author bujidao
 */
@RequiredArgsConstructor
public class McpStrictAccessTokenFilter extends OncePerRequestFilter {

    private static final String ERROR_MESSAGE = "MCP endpoint requires a valid access token";

    private final YudaoMcpServerProperties properties;
    private final SecurityProperties securityProperties;
    private final OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String bearerToken = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        OAuth2AccessTokenCheckRespDTO accessToken = checkAccessToken(bearerToken);
        if (!isStrictAccessToken(bearerToken, accessToken)) {
            response.setHeader("WWW-Authenticate",
                    McpBearerAuthenticationHeaders.unauthorized(request, properties));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ERROR_MESSAGE);
            return;
        }

        if (StrUtil.isNotBlank(accessToken.getClientId())) {
            loginUser.setContext(McpTransportContextKeys.CLIENT_ID, accessToken.getClientId());
        }
        filterChain.doFilter(request, response);
    }

    private OAuth2AccessTokenCheckRespDTO checkAccessToken(String bearerToken) {
        if (StrUtil.isBlank(bearerToken)) {
            return null;
        }
        try {
            return oauth2TokenApi.checkAccessToken(bearerToken);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isStrictAccessToken(String bearerToken, OAuth2AccessTokenCheckRespDTO accessToken) {
        if (StrUtil.isBlank(bearerToken) || accessToken == null) {
            return false;
        }
        if (!bearerToken.equals(accessToken.getAccessToken())) {
            return false;
        }
        return !bearerToken.equals(accessToken.getRefreshToken());
    }

}
