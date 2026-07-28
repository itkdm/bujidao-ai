package cn.iocoder.yudao.framework.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 允许通过 MCP 暴露的 ACF 能力名称。默认为空，避免新增能力后被意外公开。
     */
    private List<String> exposedCapabilities = new ArrayList<>();

    /**
     * 是否允许暴露具有副作用的能力。
     */
    private boolean allowSideEffects;

    /**
     * 是否允许暴露需要人工确认的能力。
     */
    private boolean allowConfirmationRequired;

}
