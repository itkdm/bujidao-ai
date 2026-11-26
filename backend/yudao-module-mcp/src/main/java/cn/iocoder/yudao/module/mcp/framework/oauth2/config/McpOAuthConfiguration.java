package cn.iocoder.yudao.module.mcp.framework.oauth2.config;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdFetcher;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdMetadataCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP OAuth 配置。
 *
 * @author bujidao
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpOAuthProperties.class)
public class McpOAuthConfiguration {

    /**
     * CIMD 文档拉取器（Spring Bean，供 {@code McpCimdClientService} 构造器注入）。
     * 与 {@code McpCimdClientService} 用同一个 enabled 开关管控。
     */
    @Bean
    @ConditionalOnProperty(prefix = McpOAuthProperties.PREFIX, name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public McpCimdFetcher mcpCimdFetcher(McpOAuthProperties properties) {
        return new McpCimdFetcher(McpOAuthProperties.cimdOrDefault(properties),
                JsonUtils.getObjectMapper());
    }

    /**
     * CIMD 文档内存缓存（Spring Bean，供 {@code McpCimdClientService} 构造器注入）。
     * 与 {@code McpCimdClientService} 用同一个 enabled 开关管控。
     */
    @Bean
    @ConditionalOnProperty(prefix = McpOAuthProperties.PREFIX, name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public McpCimdMetadataCache mcpCimdMetadataCache(McpOAuthProperties properties) {
        McpOAuthProperties.Cimd cimd = McpOAuthProperties.cimdOrDefault(properties);
        return new McpCimdMetadataCache(cimd.getCacheMaxAge(), cimd.getCacheDefaultTtl(),
                cimd.getCacheMaxEntries());
    }

}
