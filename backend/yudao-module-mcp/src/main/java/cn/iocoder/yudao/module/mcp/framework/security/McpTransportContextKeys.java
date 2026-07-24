package cn.iocoder.yudao.module.mcp.framework.security;

/**
 * MCP 传输层向工具调用层传递的可信上下文键。
 *
 * @author bujidao
 */
public final class McpTransportContextKeys {

    public static final String USER_ID = "bujidao.mcp.user-id";
    public static final String TENANT_ID = "bujidao.mcp.tenant-id";
    public static final String CONSUMER_ID = "bujidao.mcp.consumer-id";

    private McpTransportContextKeys() {
    }

}
