package cn.iocoder.yudao.framework.acf.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ACF 配置。
 *
 * @author bujidao
 */
@Data
@ConfigurationProperties(prefix = YudaoAcfProperties.PREFIX)
public class YudaoAcfProperties {

    public static final String PREFIX = "yudao.acf";

    /**
     * 执行前确认配置。
     */
    private final Confirmation confirmation = new Confirmation();

    @Data
    public static class Confirmation {

        /**
         * 是否启用执行前确认挑战。
         *
         * 普通 MCP 客户端通常无法提供真正的人机确认 UI，默认关闭，避免把确认退化为模型自动多调一次。
         * 自研 Agent Runtime、审批流或支持 MCP elicitation 的客户端接入后，可显式开启。
         */
        private boolean enabled = false;

    }

}
