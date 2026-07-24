package cn.iocoder.yudao.module.mcp.framework.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * MCP Server 配置
 *
 * @author bujidao
 */
@Data
@Validated
@ConfigurationProperties(prefix = YudaoMcpServerProperties.PREFIX)
public class YudaoMcpServerProperties {

    public static final String PREFIX = "yudao.mcp.server";

    private boolean enabled;

    @NotBlank
    private String endpoint = "/mcp";

    @NotBlank
    private String name = "bujidao-mcp-server";

    @NotBlank
    private String version = "1.0.0";

    private String instructions;

    private Duration requestTimeout = Duration.ofSeconds(30);

    @AssertTrue(message = "request-timeout must be greater than zero")
    public boolean isRequestTimeoutValid() {
        return requestTimeout != null && !requestTimeout.isZero() && !requestTimeout.isNegative();
    }

}
