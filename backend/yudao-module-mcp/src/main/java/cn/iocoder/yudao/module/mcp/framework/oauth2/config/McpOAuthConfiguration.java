package cn.iocoder.yudao.module.mcp.framework.oauth2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP OAuth 配置。
 *
 * @author bujidao
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpOAuthProperties.class)
public class McpOAuthConfiguration {
}
