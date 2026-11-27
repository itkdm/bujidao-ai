package cn.iocoder.yudao.framework.mcp.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;
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
     * <p>该开关只影响 MCP discovery 元数据；真正的注册端点是否可用由
     * {@code yudao.mcp.oauth.dynamic-client-registration-enabled} 控制。
     * 默认关闭：不配即不暴露，部署方显式开启才向客户端宣告 DCR。
     * 注意：只开本开关、不开端点开关时，元数据会宣告一个不可用的注册端点，
     * 两开关须配套开启。
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

    /**
     * 单次 MCP 请求允许读取的最大请求体，避免 chunked 请求绕过 Content-Length 检查。
     */
    private DataSize maxRequestSize = DataSize.ofMegabytes(1);

    /**
     * MRTR {@code requestState} 的签名配置。
     */
    private final RequestState requestState = new RequestState();

    /**
     * Legacy MCP 2025-11-25 兼容腿的开关。
     */
    private final Legacy legacy = new Legacy();

    /**
     * Legacy MCP 2025-11-25 兼容腿的开关。
     *
     * <p>默认开启：同一 endpoint（{@link #endpoint}）同时服务 modern（2026-07-28）
     * 与 legacy（2025-11-25 stateless）两条腿，老客户端（如 WorkBuddy）走 legacy
     * 腿。改成 {@code false} 后分流到 legacy 的请求一律被分流器明确拒绝
     * （HTTP 400 + JSON-RPC {@code -32022}），不会静默落到 modern 腿；
     * modern 请求完全不受影响。</p>
     *
     * <p>开关只关分流入口，不拆 legacy bean：legacy transport 与 stateless server
     * 仍会建出来（开销很小，且 legacy transport 本来就没有 URL 映射），分流器是
     * 唯一的路由决策点，关掉它就等于关掉整条 legacy 腿，不存在 bean 缺失导致
     * NPE 的分支。</p>
     */
    @Data
    public static class Legacy {

        /**
         * 是否开启 legacy MCP 2025-11-25 兼容，默认 true。
         */
        private boolean enabled = true;

    }

    /**
     * MRTR {@code requestState} 的签名配置。
     *
     * <p>服务端把 challengeId 放进 {@code requestState} 随 InputRequiredResult 返回，
     * SDK 在发送前用 HMAC-SHA256 签名（默认 10 分钟过期，绑定 tools/call 方法与工具名、
     * 以及当前用户），重试时先验签开封。注意签名不是加密：payload 是 base64url 编码
     * 的明文 JSON，客户端可读，但改不了。密钥只影响“哪个实例能开封”，不影响 challenge
     * 本身的校验（用户、租户、幂等键、请求摘要、过期仍由服务端 challenge 记录校验）。</p>
     */
    @Data
    public static class RequestState {

        /**
         * 多实例共享的 HMAC-SHA256 密钥，标准 Base64 编码，解码后至少 32 字节。
         *
         * <p>不配时 SDK 每个进程随机生成一个 key（启动日志有警告）：单实例够用；
         * 多实例部署下 A 实例签名的 requestState 在 B 实例打不开，重试会报
         * invalid params，确认流程走不下去。配了共享 key 后任意实例都能开封。</p>
         *
         * <p>密钥只能来自环境变量或密钥管理服务，绝不要把真实值写进仓库里的配置
         * 文件；轮换后旧 state 立即失效（进行中的确认需重调一次工具）。</p>
         */
        private String sharedKey;

    }

    @AssertTrue(message = "request-state.shared-key must be Base64 and decode to at least 32 bytes")
    public boolean isRequestStateSharedKeyValid() {
        if (requestState == null || requestState.getSharedKey() == null
                || requestState.getSharedKey().isBlank()) {
            return true;
        }
        try {
            return Base64.getDecoder().decode(requestState.getSharedKey().trim()).length >= 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "max-request-size must be greater than zero and fit in an int")
    public boolean isMaxRequestSizeValid() {
        return maxRequestSize != null && maxRequestSize.toBytes() > 0
                && maxRequestSize.toBytes() <= Integer.MAX_VALUE;
    }

    @AssertTrue(message = "endpoint must be a fixed path starting with '/' and must not be root")
    public boolean isEndpointValid() {
        return endpoint != null && endpoint.startsWith("/") && endpoint.length() > 1
                && !endpoint.contains("*") && !endpoint.contains("?") && !endpoint.contains("#")
                && endpoint.chars().noneMatch(Character::isWhitespace);
    }

}
