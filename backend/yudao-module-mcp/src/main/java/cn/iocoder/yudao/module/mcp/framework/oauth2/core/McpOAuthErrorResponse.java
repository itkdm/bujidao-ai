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
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of("error", error, "error_description", description));
    }

}
