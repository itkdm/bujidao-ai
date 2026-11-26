package cn.iocoder.yudao.module.mcp.framework.oauth2.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
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

    /**
     * CIMD（Client ID Metadata Document）拉取、校验与缓存配置。
     */
    @Valid
    @NotNull
    private Cimd cimd = new Cimd();

    /**
     * 取 cimd 配置段：编程式构造未设 cimd 时返回默认值，不 NPE。
     * 绑定层有 @NotNull + 字段默认值保证，正常启动走不到 null 分支。
     */
    public static Cimd cimdOrDefault(McpOAuthProperties properties) {
        if (properties == null || properties.getCimd() == null) {
            return new Cimd();
        }
        return properties.getCimd();
    }

    /**
     * CIMD 配置段，前缀 yudao.mcp.oauth.cimd。
     */
    @Data
    public static class Cimd {

        /**
         * 拉取文档的连接超时与请求超时。
         */
        @NotNull
        private Duration fetchTimeout = Duration.ofSeconds(3);

        /**
         * 响应 body 大小上限（字节），超限拒绝。
         */
        @Min(1)
        private int maxDocumentBytes = 5120;

        /**
         * 缓存硬上限：响应 Cache-Control 的 max-age 再大也不超过它。
         */
        @NotNull
        private Duration cacheMaxAge = Duration.ofHours(24);

        /**
         * 响应无缓存头时的默认缓存 TTL。
         */
        @NotNull
        private Duration cacheDefaultTtl = Duration.ofHours(1);

        /**
         * 缓存条目数上限，超限时新文档不入缓存（每次现拉，不报错）。
         */
        @Min(1)
        private int cacheMaxEntries = 1000;

        /**
         * 是否跳过“拒绝内网 / 回环地址”的 SSRF 检查，并放行 http scheme 的 client_id URL。
         * 仅供测试与本地开发用，生产必须保持 false。
         */
        private boolean allowPrivateAddresses = false;

        @AssertTrue(message = "cimd.fetchTimeout 必须大于 0")
        public boolean isFetchTimeoutPositive() {
            return fetchTimeout != null && !fetchTimeout.isNegative() && !fetchTimeout.isZero();
        }

        @AssertTrue(message = "cimd.cacheMaxAge 必须大于 0")
        public boolean isCacheMaxAgePositive() {
            return cacheMaxAge != null && !cacheMaxAge.isNegative() && !cacheMaxAge.isZero();
        }

        @AssertTrue(message = "cimd.cacheDefaultTtl 必须大于 0")
        public boolean isCacheDefaultTtlPositive() {
            return cacheDefaultTtl != null && !cacheDefaultTtl.isNegative() && !cacheDefaultTtl.isZero();
        }

    }

}
