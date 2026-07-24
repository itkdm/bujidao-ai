package cn.iocoder.yudao.module.mcp.framework.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

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

    /**
     * 允许访问 MCP Endpoint 的 Origin。SDK 支持使用 {@code :*} 匹配任意端口。
     */
    @NotEmpty
    private List<@NotBlank String> allowedOrigins = List.of(
            "http://localhost:*", "https://localhost:*",
            "http://127.0.0.1:*", "https://127.0.0.1:*");

    /**
     * 允许访问 MCP Endpoint 的 Host。远程部署时必须显式增加实际域名。
     */
    @NotEmpty
    private List<@NotBlank String> allowedHosts = List.of("localhost:*", "127.0.0.1:*");

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

    @AssertTrue(message = "endpoint must be a fixed path starting with '/' and must not be root")
    public boolean isEndpointValid() {
        return endpoint != null && endpoint.startsWith("/") && endpoint.length() > 1
                && !endpoint.contains("*") && !endpoint.contains("?") && !endpoint.contains("#")
                && endpoint.chars().noneMatch(Character::isWhitespace);
    }

}
