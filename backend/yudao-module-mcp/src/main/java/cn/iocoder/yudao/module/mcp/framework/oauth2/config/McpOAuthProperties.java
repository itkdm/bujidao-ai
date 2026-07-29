package cn.iocoder.yudao.module.mcp.framework.oauth2.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * MCP OAuth 适配配置。
 *
 * @author bujidao
 */
@ConfigurationProperties(prefix = McpOAuthProperties.PREFIX)
@Validated
@Data
public class McpOAuthProperties {

    public static final String PREFIX = "yudao.mcp.oauth";

    /**
     * 是否开启 MCP OAuth 适配端点。
     */
    private boolean enabled = true;

    /**
     * 是否开启 OAuth2 Dynamic Client Registration。
     */
    private boolean dynamicClientRegistrationEnabled = true;

    /**
     * 动态客户端编号前缀。
     */
    @NotBlank
    private String clientIdPrefix = "mcp-dcr-";

    /**
     * 未申请 scope 时授予的默认 scope；申请 scope 时只允许该列表的子集。
     */
    @NotEmpty
    private List<@NotBlank String> defaultScopes = List.of("mcp:access");

    /**
     * 允许本地回调的 host。
     */
    @NotEmpty
    private List<@NotBlank String> localRedirectHosts = List.of("localhost", "127.0.0.1");

    /**
     * 是否允许 native app 私有 URI scheme，例如 workbuddy://、vscode://。
     */
    private boolean allowPrivateUseUriSchemeRedirects = true;

    /**
     * 额外允许的 redirect_uri 前缀，用于部署方显式开放 HTTPS 回调等场景。
     */
    private List<String> allowedRedirectUriPrefixes = List.of();

    /**
     * 动态客户端 access token 有效期。
     */
    @Min(60)
    private Integer accessTokenValiditySeconds = 1800;

    /**
     * 动态客户端 refresh token 有效期。
     */
    @Min(300)
    private Integer refreshTokenValiditySeconds = 2592000;

    /**
     * DCR 请求未提供 client_name 时使用的默认名称。
     */
    @NotBlank
    private String defaultClientName = "MCP Client";

    /**
     * OAuth2 Client 表要求 logo 非空，DCR 请求未提供时使用该占位图标。
     */
    @NotBlank
    private String defaultLogo = "https://www.iocoder.cn/favicon.ico";

}
