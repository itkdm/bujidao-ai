package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.modelcontextprotocol.common.McpTransportContext;

import java.util.Map;

/**
 * MCP 到 ACF 的可信调用上下文构造工具。
 *
 * @author bujidao
 */
final class AcfMcpContextSupport {

    private static final String SOURCE = "MCP";
    private static final String OAUTH_CLIENT_ID_ATTRIBUTE = "oauthClientId";

    private AcfMcpContextSupport() {
    }

    static CapabilityContext fromTransport(McpTransportContext transportContext, String clientRequestId) {
        return CapabilityContext.builder()
                .userId(value(transportContext, McpTransportContextKeys.USER_ID, Long.class))
                .tenantId(value(transportContext, McpTransportContextKeys.TENANT_ID, Long.class))
                .source(SOURCE)
                .consumerType(CapabilityConsumerType.MCP)
                .consumerId(value(transportContext, McpTransportContextKeys.CONSUMER_ID, String.class))
                .clientRequestId(clientRequestId)
                .attributes(attributes(value(transportContext, McpTransportContextKeys.CLIENT_ID, String.class)))
                .build();
    }

    static CapabilityContext fromLoginUser() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            return CapabilityContext.empty();
        }
        Long tenantId = loginUser.getVisitTenantId() != null
                ? loginUser.getVisitTenantId() : loginUser.getTenantId();
        String clientId = loginUser.getContext(McpTransportContextKeys.CLIENT_ID, String.class);
        return CapabilityContext.builder()
                .userId(loginUser.getId())
                .tenantId(tenantId)
                .source(SOURCE)
                .consumerType(CapabilityConsumerType.MCP)
                .consumerId("user:" + loginUser.getId())
                .attributes(attributes(clientId))
                .build();
    }

    private static Map<String, Object> attributes(String clientId) {
        return clientId == null || clientId.isBlank() ? Map.of() : Map.of(OAUTH_CLIENT_ID_ATTRIBUTE, clientId);
    }

    private static <T> T value(McpTransportContext context, String key, Class<T> type) {
        if (context == null) {
            return null;
        }
        Object value = context.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

}
