package cn.iocoder.yudao.framework.mcp.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 校验认证用户是否具备访问 MCP Endpoint 所需的授权范围。
 *
 * 仅读取服务端认证结果中的 scope，不接受请求参数或 Tool arguments 携带的 scope。
 * 未认证请求由认证入口点返回 401，本过滤器只负责已认证请求的 403 判定。
 *
 * @author bujidao
 */
public class McpScopeAuthorizationFilter extends OncePerRequestFilter {

    private static final String ERROR_MESSAGE = "Authenticated user lacks the required scope for the MCP endpoint";

    private final Set<String> requiredScopes;

    public McpScopeAuthorizationFilter(List<String> requiredScopes) {
        this.requiredScopes = Set.copyOf(requiredScopes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            // 交给后续的认证判定返回 401，避免把未认证请求误报为权限不足
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasRequiredScopes(loginUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ERROR_MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasRequiredScopes(LoginUser loginUser) {
        List<String> grantedScopes = loginUser.getScopes();
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            return false;
        }
        return grantedScopes.containsAll(requiredScopes);
    }

}
