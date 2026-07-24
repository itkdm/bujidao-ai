package cn.iocoder.yudao.module.mcp.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP Tool 暴露配置。
 *
 * @author bujidao
 */
@ConfigurationProperties(prefix = YudaoMcpToolProperties.PREFIX)
@Data
public class YudaoMcpToolProperties {

    public static final String PREFIX = "yudao.mcp.tools";

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
