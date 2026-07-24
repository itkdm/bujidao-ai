package cn.iocoder.yudao.module.mcp.framework.security;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.yudao.module.mcp.framework.config.YudaoMcpServerProperties;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 要求 MCP HTTP 端点通过芋道现有 Token 认证。
 *
 * @author bujidao
 */
public class McpAuthorizeRequestsCustomizer extends AuthorizeRequestsCustomizer {

    private final YudaoMcpServerProperties properties;

    public McpAuthorizeRequestsCustomizer(YudaoMcpServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void customize(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(properties.getEndpoint()).authenticated();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
