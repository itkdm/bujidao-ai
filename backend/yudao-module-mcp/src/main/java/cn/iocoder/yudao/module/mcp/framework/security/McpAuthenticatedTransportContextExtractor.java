package cn.iocoder.yudao.module.mcp.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从芋道认证上下文提取 MCP 调用身份。
 *
 * 外部请求不能直接声明 userId、tenantId 等身份字段；这里只信任已经由 Security Starter
 * 校验并写入 SecurityContext 的 {@link LoginUser}。
 *
 * @author bujidao
 */
public class McpAuthenticatedTransportContextExtractor
        implements McpTransportContextExtractor<HttpServletRequest> {

    @Override
    public McpTransportContext extract(HttpServletRequest request) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new IllegalStateException("Authenticated LoginUser is required for MCP requests");
        }
        Long tenantId = loginUser.getVisitTenantId() != null
                ? loginUser.getVisitTenantId() : loginUser.getTenantId();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(McpTransportContextKeys.USER_ID, loginUser.getId());
        if (tenantId != null) {
            values.put(McpTransportContextKeys.TENANT_ID, tenantId);
        }
        values.put(McpTransportContextKeys.CONSUMER_ID, "user:" + loginUser.getId());
        return McpTransportContext.create(values);
    }

}
