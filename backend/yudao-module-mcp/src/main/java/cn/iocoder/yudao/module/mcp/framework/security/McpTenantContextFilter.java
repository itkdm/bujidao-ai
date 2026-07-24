package cn.iocoder.yudao.module.mcp.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * 将 MCP 请求的租户上下文收口到认证用户，防止外部 tenant-id Header 污染业务执行线程。
 *
 * @author bujidao
 */
public class McpTenantContextFilter extends OncePerRequestFilter {

    private static final String ERROR_MESSAGE = "Tenant context does not match the authenticated user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long trustedTenantId = loginUser.getVisitTenantId() != null
                ? loginUser.getVisitTenantId() : loginUser.getTenantId();
        Long requestedTenantId = WebFrameworkUtils.getTenantId(request);
        if (requestedTenantId != null && !Objects.equals(requestedTenantId, trustedTenantId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ERROR_MESSAGE);
            return;
        }

        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            setTenantId(trustedTenantId);
            filterChain.doFilter(request, response);
        } finally {
            setTenantId(previousTenantId);
        }
    }

    private static void setTenantId(Long tenantId) {
        if (tenantId == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.setTenantId(tenantId);
        }
    }

}
