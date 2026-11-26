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

    /** 限流重试间隔（秒）：与 {@code McpOAuthRateLimiter} 的 60 秒滑动窗口一致。 */
    public static final long RATE_LIMIT_RETRY_AFTER_SECONDS = 60L;

    private final HttpStatus status;
    private final String error;
    private final String description;
    /** 限流时建议客户端等待的秒数（Retry-After 头）；非限流异常为 null，不发该头。 */
    private final Long retryAfterSeconds;

    public McpOAuthException(HttpStatus status, String error, String description) {
        this(status, error, description, null);
    }

    public McpOAuthException(HttpStatus status, String error, String description, Long retryAfterSeconds) {
        super(description);
        this.status = status;
        this.error = error;
        this.description = description;
        this.retryAfterSeconds = retryAfterSeconds;
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

    /**
     * 限流：HTTP 429 + OAuth {@code temporarily_unavailable}（RFC 6749 §5.2 临时性问题），
     * 带 {@code Retry-After: 60}。不用 {@code invalid_client}（400）：合规客户端会把
     * {@code invalid_client} 当作配置错误而不重试。
     */
    public static McpOAuthException rateLimited(String description) {
        return new McpOAuthException(HttpStatus.TOO_MANY_REQUESTS, "temporarily_unavailable",
                description, RATE_LIMIT_RETRY_AFTER_SECONDS);
    }

}
