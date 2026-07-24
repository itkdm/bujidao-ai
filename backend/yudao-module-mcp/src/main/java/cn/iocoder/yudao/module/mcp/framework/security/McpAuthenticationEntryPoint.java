package cn.iocoder.yudao.module.mcp.framework.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 为 MCP Endpoint 返回标准 HTTP Bearer 认证错误，避免沿用后台接口的业务码响应语义。
 *
 * @author bujidao
 */
public class McpAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_MESSAGE = "Authentication is required to access the MCP endpoint";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ERROR_MESSAGE);
    }

}
