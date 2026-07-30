package cn.iocoder.yudao.framework.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP ACF Tool Provider 配置。
 *
 * @author bujidao
 */
@ConfigurationProperties(prefix = YudaoMcpAcfProperties.PREFIX)
@Data
public class YudaoMcpAcfProperties {

    public static final String PREFIX = "yudao.mcp.acf";

    /**
     * 是否启用 ACF 默认 Tool Provider。
     */
    private boolean enabled = true;

}
