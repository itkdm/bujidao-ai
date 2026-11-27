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
     *
     * <p>默认开启：恢复 DCR 后公开注册端点即对外开放。公网暴露部署时应评估是否关闭，
     * 或结合 redirect_uri 策略与注册限流收紧。注意：Step 2 的元数据开关默认关闭
     * 只能藏住入口、关不了端点本身，真正的开关是这里。
     */
    private boolean dynamicClientRegistrationEnabled = true;

    /**
     * 动态客户端编号前缀。
     *
     * <p>同意页用此前缀识别 DCR 注册的客户端并显示“未验证”标记；改动前缀须同步改
     * 前端判断，否则标记失效（fail-open：不显示标记）。
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
     *
     * <p>默认开启：放行符合私有 scheme 形态的回调。公网暴露部署时如无桌面客户端
     * 需求可关闭收紧，只放行回环与前缀白名单内的回调。
     */
    private boolean allowPrivateUseUriSchemeRedirects = true;

    /**
     * 额外允许的 redirect_uri 前缀，用于部署方显式开放 HTTPS 回调等场景。
     */
    private List<@NotBlank String> allowedRedirectUriPrefixes = List.of();

    /**
     * MCP OAuth 客户端 access token 有效期。
     */
    @NotNull
    @Min(60)
    private Integer accessTokenValiditySeconds = 1800;

    /**
     * MCP OAuth 客户端 refresh token 有效期。
     */
    @NotNull
    @Min(300)
    private Integer refreshTokenValiditySeconds = 2592000;

    /**
     * DCR 请求未提供 client_name 时使用的默认名称。
     */
    @NotBlank
    private String defaultClientName = "MCP Client";

    /**
     * OAuth2 Client 表要求 logo 非空，DCR 请求未提供 logo 时使用该占位图标。
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
     * 限流配置：CIMD 拉取（按目标 host + 全局 + 并发）与授权 / token 端点（按调用方 IP）。
     */
    @Valid
    @NotNull
    private RateLimit rateLimit = new RateLimit();

    /**
     * 取 rateLimit 配置段：编程式构造未设 rateLimit 时返回默认值，不 NPE。
     * 绑定层有 @NotNull + 字段默认值保证，正常启动走不到 null 分支。
     */
    public static RateLimit rateLimitOrDefault(McpOAuthProperties properties) {
        if (properties == null || properties.getRateLimit() == null) {
            return new RateLimit();
        }
        return properties.getRateLimit();
    }

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

    /**
     * 限流配置段，前缀 yudao.mcp.oauth.rate-limit。
     *
     * <p>阈值口径（均为 60 秒滑动窗口）：
     * <ul>
     *     <li>CIMD 拉取只在缓存未命中时发生（默认缓存 1 小时），正常客户端一个 host 每分钟
     *     远到不了 60 次；60 次 / host 既能挡住单个恶意 host 反复拉取拖垮拉取线程，
     *     又不会误杀正常客户端。</li>
     *     <li>全局 600 次 / 分钟是防拖垮的安全阀，不是针对某个客户端的配额。</li>
     *     <li>并发 20 是拉取线程占用上限（单次拉取超时 3 秒），不是吞吐目标。</li>
     *     <li>授权同意是人点一次的操作，60 次 / 分钟 / IP 足够覆盖重试与多人共用出口 IP；
     *     token 兑换每次登录 / 刷新才调一次，60 次 / 分钟 / IP 同理，且授权码本身
     *     高熵、一次失效，60 次试错空间形不成有效爆破。</li>
     *     <li>动态注册是更低频的操作：客户端按 issuer 持久化注册结果，正常只注册一次；
     *     10 次 / 分钟 / IP 是单 IP 速率上限，防单点刷注册（client sprawl）；分布式 IP
     *     与多实例部署不在覆盖内（限流器只约束单实例），需网关层再限；也不会误伤
     *     多个桌面客户端共用出口 IP 的正常场景。</li>
     * </ul>
     */
    @Data
    public static class RateLimit {

        /**
         * 是否开启限流。关闭后所有限流检查直接放行（仅供排障临时用，生产保持 true）。
         */
        private boolean enabled = true;

        /**
         * 单个目标 host 每分钟最多 CIMD 拉取次数。
         */
        @Min(1)
        private int cimdPerHostPerMinute = 60;

        /**
         * 全部 host 每分钟合计最多 CIMD 拉取次数。
         */
        @Min(1)
        private int cimdGlobalPerMinute = 600;

        /**
         * CIMD 拉取最大并发数，超限的拉取直接拒绝、不排队。
         */
        @Min(1)
        private int cimdMaxConcurrentFetches = 20;

        /**
         * 单个调用方 IP 每分钟最多授权同意（POST /mcp/oauth2/authorize）次数。
         */
        @Min(1)
        private int authorizePerIpPerMinute = 60;

        /**
         * 单个调用方 IP 每分钟最多 token 兑换（POST /mcp/oauth2/token）次数。
         */
        @Min(1)
        private int tokenPerIpPerMinute = 60;

        /**
         * 单个调用方 IP 每分钟最多动态客户端注册（POST /mcp/oauth2/register）次数。
         */
        @Min(1)
        private int registerPerIpPerMinute = 10;

    }

}
