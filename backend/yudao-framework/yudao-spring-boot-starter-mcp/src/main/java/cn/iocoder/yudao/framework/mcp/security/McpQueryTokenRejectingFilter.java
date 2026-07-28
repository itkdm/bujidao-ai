package cn.iocoder.yudao.framework.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 拒绝通过 URL 查询参数传递 MCP 访问令牌，避免令牌进入访问日志、浏览器历史等位置。
 *
 * @author bujidao
 */
public class McpQueryTokenRejectingFilter extends OncePerRequestFilter {

    private static final String BEARER_CHALLENGE = "Bearer";
    private static final String ERROR_MESSAGE = "Bearer token must be provided in the Authorization header";

    private final String tokenParameter;

    public McpQueryTokenRejectingFilter(String tokenParameter) {
        this.tokenParameter = tokenParameter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getParameterMap().containsKey(tokenParameter)) {
            response.setHeader("WWW-Authenticate", BEARER_CHALLENGE);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ERROR_MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

}
