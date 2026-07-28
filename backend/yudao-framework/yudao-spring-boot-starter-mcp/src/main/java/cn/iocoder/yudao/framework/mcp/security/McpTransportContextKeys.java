package cn.iocoder.yudao.framework.mcp.security;

/**
 * MCP 传输层向工具调用层传递的可信上下文键。
 *
 * @author bujidao
 */
public final class McpTransportContextKeys {

    public static final String USER_ID = "bujidao.mcp.user-id";
    public static final String TENANT_ID = "bujidao.mcp.tenant-id";
    public static final String CONSUMER_ID = "bujidao.mcp.consumer-id";
    public static final String CLIENT_ID = "bujidao.mcp.client-id";

    private McpTransportContextKeys() {
    }

}
