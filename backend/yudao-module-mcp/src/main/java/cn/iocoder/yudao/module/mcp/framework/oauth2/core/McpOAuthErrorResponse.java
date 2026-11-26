package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * MCP OAuth 标准错误响应。
 *
 * @author bujidao
 */
public class McpOAuthErrorResponse {

    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String description) {
        return error(status, error, description, null);
    }

    /**
     * 标准 OAuth 错误响应：{@code Cache-Control: no-store} + {@code Pragma: no-cache}。
     * {@code retryAfterSeconds} 非 null 时加 {@code Retry-After} 头（限流用）。
     */
    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error,
                                                            String description, Long retryAfterSeconds) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache");
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        return builder.body(Map.of("error", error, "error_description", description));
    }

    /** 按异常自带的状态 / 错误码 / 描述 / Retry-After 生成标准 OAuth 错误响应。 */
    public static ResponseEntity<Map<String, Object>> error(McpOAuthException exception) {
        return error(exception.getStatus(), exception.getError(), exception.getDescription(),
                exception.getRetryAfterSeconds());
    }

}
