package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * MCP OAuth resource URI 解析器。
 *
 * @author bujidao
 */
@Component
@RequiredArgsConstructor
public class McpOAuthResourceUriResolver {

    private final YudaoMcpServerProperties mcpServerProperties;

    public String resolve(HttpServletRequest request) {
        if (StrUtil.isNotBlank(mcpServerProperties.getPublicResourceUri())) {
            return mcpServerProperties.getPublicResourceUri();
        }
        return absolute(origin(request), mcpServerProperties.getEndpoint());
    }

    private static String absolute(String origin, String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return origin + (value.startsWith("/") ? value : "/" + value);
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
