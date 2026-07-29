package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * MCP OAuth 协议异常。
 *
 * @author bujidao
 */
@Getter
public class McpOAuthException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final String description;

    public McpOAuthException(HttpStatus status, String error, String description) {
        super(description);
        this.status = status;
        this.error = error;
        this.description = description;
    }

    public static McpOAuthException invalidRequest(String description) {
        return new McpOAuthException(HttpStatus.BAD_REQUEST, "invalid_request", description);
    }

    public static McpOAuthException invalidGrant(String description) {
        return new McpOAuthException(HttpStatus.BAD_REQUEST, "invalid_grant", description);
    }

    public static McpOAuthException invalidClient(String description) {
        return new McpOAuthException(HttpStatus.BAD_REQUEST, "invalid_client", description);
    }

    public static McpOAuthException invalidScope(String description) {
        return new McpOAuthException(HttpStatus.BAD_REQUEST, "invalid_scope", description);
    }

}
