package cn.iocoder.yudao.module.mcp.enums;

/**
 * MCP Redis Key 常量。
 *
 * @author bujidao
 */
public interface McpRedisKeyConstants {

    /**
     * MCP OAuth 授权码扩展信息。
     *
     * KEY 格式：mcp_oauth2_authorization_code_extra:{code}
     * VALUE 数据类型：String JSON
     */
    String MCP_OAUTH2_AUTHORIZATION_CODE_EXTRA = "mcp_oauth2_authorization_code_extra:%s";

    /**
     * MCP OAuth 授权请求已处理标记。
     *
     * KEY 格式：mcp_oauth2_authorization_request_used:{requestId}
     * VALUE 数据类型：String
     */
    String MCP_OAUTH2_AUTHORIZATION_REQUEST_USED = "mcp_oauth2_authorization_request_used:%s";

}
