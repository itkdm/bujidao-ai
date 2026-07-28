package cn.iocoder.yudao.framework.mcp.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * 构造 MCP HTTP Bearer 认证响应头。
 *
 * @author bujidao
 */
public final class McpBearerAuthenticationHeaders {

    private static final String PROTECTED_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";

    private McpBearerAuthenticationHeaders() {
    }

    public static String unauthorized(HttpServletRequest request, YudaoMcpServerProperties properties) {
        StringBuilder builder = new StringBuilder("Bearer");
        append(builder, "resource_metadata", protectedResourceMetadataUrl(request, properties));
        append(builder, "scope", scope(properties.getRequiredScopes()));
        return builder.toString();
    }

    public static String insufficientScope(HttpServletRequest request, YudaoMcpServerProperties properties) {
        StringBuilder builder = new StringBuilder("Bearer");
        append(builder, "error", "insufficient_scope");
        append(builder, "scope", scope(properties.getRequiredScopes()));
        append(builder, "resource_metadata", protectedResourceMetadataUrl(request, properties));
        return builder.toString();
    }

    private static String protectedResourceMetadataUrl(HttpServletRequest request,
                                                       YudaoMcpServerProperties properties) {
        return origin(request) + PROTECTED_RESOURCE_METADATA_PATH + properties.getEndpoint();
    }

    private static String scope(List<String> scopes) {
        return scopes == null ? "" : String.join(" ", scopes);
    }

    private static void append(StringBuilder builder, String name, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        builder.append(' ').append(name).append("=\"").append(escape(value)).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String origin(HttpServletRequest request) {
        return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath(null)
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
    }

}
