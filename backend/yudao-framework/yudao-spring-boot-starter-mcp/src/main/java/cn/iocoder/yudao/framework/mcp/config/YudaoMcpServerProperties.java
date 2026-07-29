package cn.iocoder.yudao.framework.mcp.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
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

    /**
     * 访问 MCP Endpoint 必须具备的授权范围，取自认证 Token 的 scope。
     *
     * 采用「全部满足」语义：认证用户缺少其中任意一项即返回 403。
     * 该配置不允许为空，避免把 MCP Endpoint 降级为「任意已认证用户可用」。
     */
    @NotEmpty
    private List<@NotBlank String> requiredScopes = List.of("mcp:access");

    /**
     * MCP 资源的外部访问地址。未配置时按当前请求推导。
     */
    private String publicResourceUri;

    /**
     * OAuth 授权服务器 issuer。未配置时按当前请求 Host 推导。
     */
    private String authorizationServerIssuer;

    /**
     * 浏览器授权入口。未配置时使用 yudao.web.admin-ui.url + /mcp/sso。
     */
    private String authorizationEndpoint;

    /**
     * OAuth token endpoint。未配置时使用 yudao.web.admin-api.prefix + /mcp/oauth2/token。
     */
    private String tokenEndpoint;

    /**
     * OAuth token revoke endpoint。未配置时使用 yudao.web.admin-api.prefix + /mcp/oauth2/revoke。
     */
    private String revocationEndpoint;

    /**
     * 是否在 OAuth Authorization Server Metadata 中暴露 Dynamic Client Registration 端点。
     *
     * 该开关只影响 MCP discovery 元数据；真正的注册端点是否可用由授权服务器自行控制。
     */
    private boolean dynamicClientRegistrationEnabled = false;

    /**
     * OAuth Dynamic Client Registration endpoint。未配置时使用
     * yudao.web.admin-api.prefix + /mcp/oauth2/register。
     */
    private String registrationEndpoint;

    @NotBlank
    private String name = "bujidao-mcp-server";

    private String resourceDocumentation;

    @NotBlank
    private String version = "1.0.0";

    private String instructions;

    private Duration requestTimeout = Duration.ofSeconds(30);

    /**
     * 单次 MCP 请求允许读取的最大请求体，避免 chunked 请求绕过 Content-Length 检查。
     */
    private DataSize maxRequestSize = DataSize.ofMegabytes(1);

    @AssertTrue(message = "request-timeout must be greater than zero")
    public boolean isRequestTimeoutValid() {
        return requestTimeout != null && !requestTimeout.isZero() && !requestTimeout.isNegative();
    }

    @AssertTrue(message = "max-request-size must be greater than zero")
    public boolean isMaxRequestSizeValid() {
        return maxRequestSize != null && maxRequestSize.toBytes() > 0;
    }

    @AssertTrue(message = "endpoint must be a fixed path starting with '/' and must not be root")
    public boolean isEndpointValid() {
        return endpoint != null && endpoint.startsWith("/") && endpoint.length() > 1
                && !endpoint.contains("*") && !endpoint.contains("?") && !endpoint.contains("#")
                && endpoint.chars().noneMatch(Character::isWhitespace);
    }

}
