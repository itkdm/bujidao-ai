package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CIMD 文档纯校验逻辑，不依赖 Spring，可直接单测。
 *
 * <p>规则来源：draft-ietf-oauth-client-id-metadata-document + MCP 规范 OAuth 2.1 章节。
 * 本块只做“能静态判定”的校验：URL 形状、文档字段、redirect_uri scheme。
 * 动态行为（拉取、SSRF 解析检查）由 {@link McpCimdFetcher} 负责。
 *
 * @author bujidao
 */
public class McpCimdDocumentValidator {

    private static final Set<String> ALLOWED_GRANT_TYPES = Set.of("authorization_code", "refresh_token");
    private static final String AUTH_METHOD_NONE = "none";

    /** 仅用于把文档里的 jwks 对象序列化回原始 JSON 文本，不做任何业务解析。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpCimdDocumentValidator() {
    }

    // ========== client_id URL 形状 ==========

    /**
     * 校验 client_id URL 形状（生产默认：只允许 https）。
     *
     * @return 解析后的 URI
     */
    public static URI validateClientIdUrl(String clientIdUrl) throws McpCimdException {
        return validateClientIdUrl(clientIdUrl, false);
    }

    /**
     * 校验 client_id URL 形状。
     *
     * @param allowHttp 是否放行 http scheme，仅当 allowPrivateAddresses=true（本地开发 / 测试）时传 true，
     *                  生产环境必须为 false
     * @return 解析后的 URI
     */
    public static URI validateClientIdUrl(String clientIdUrl, boolean allowHttp) throws McpCimdException {
        if (clientIdUrl == null || clientIdUrl.isBlank()) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id is blank");
        }
        URI uri;
        try {
            uri = URI.create(clientIdUrl);
        } catch (IllegalArgumentException exception) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id is not a valid URI", exception);
        }
        if (uri.getFragment() != null || uri.getRawFragment() != null) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id must not contain a fragment");
        }
        if (uri.getRawUserInfo() != null) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id must not contain userinfo");
        }
        String scheme = uri.getScheme();
        boolean schemeOk = "https".equalsIgnoreCase(scheme) || (allowHttp && "http".equalsIgnoreCase(scheme));
        if (!schemeOk) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id scheme must be https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id must have a host");
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL, "client_id must have a non-root path");
        }
        for (String segment : path.split("/", -1)) {
            // 同时拦 %2E 编码的单点 / 双点段，防止绕过
            String decoded = segment.toLowerCase(Locale.ROOT).replace("%2e", ".");
            if (".".equals(decoded) || "..".equals(decoded)) {
                throw new McpCimdException(McpCimdError.INVALID_CLIENT_ID_URL,
                        "client_id path must not contain dot segments");
            }
        }
        // 注意：query 允许出现。草案对 client_id 的 query 只是 SHOULD NOT，不拦截，
        // 避免误杀用查询参数区分租户 / 环境的合法客户端。
        return uri;
    }

    // ========== 文档校验 ==========

    /**
     * 校验拉取到的 CIMD 文档并转成不可变值对象。
     *
     * @param clientIdUrl        拉取文档用的 URL，文档内 client_id 必须与它逐字节相等
     * @param document           解析后的 JSON 对象
     * @param localRedirectHosts 允许 http 回调的 host（来自 McpOAuthProperties.localRedirectHosts）
     */
    public static McpCimdMetadata validateDocument(String clientIdUrl, Map<String, Object> document,
                                                   Collection<String> localRedirectHosts) throws McpCimdException {
        if (document == null) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "document is null");
        }
        Object clientId = document.get("client_id");
        if (!(clientId instanceof String) || !clientIdUrl.equals(clientId)) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "client_id mismatch");
        }
        String clientName = requireNonBlankString(document, "client_name");

        List<String> redirectUris = requireNonEmptyStringList(document, "redirect_uris");
        for (String redirectUri : redirectUris) {
            validateRedirectUri(redirectUri, localRedirectHosts);
        }

        String tokenEndpointAuthMethod = optionalString(document, "token_endpoint_auth_method");
        if (tokenEndpointAuthMethod == null) {
            tokenEndpointAuthMethod = AUTH_METHOD_NONE;
        } else if (!AUTH_METHOD_NONE.equals(tokenEndpointAuthMethod)) {
            // 只支持 none。shared-secret 类方法
            // （client_secret_basic / client_secret_post / client_secret_jwt 等）
            // 要求服务端保管客户端密钥，与 CIMD“客户端自带身份、无预注册密钥”的模型冲突，一律拒绝；
            // private_key_jwt（MCP 规范的可选能力）现阶段也不支持：Resolver 会把
            // token_endpoint_auth_method 硬编码为 none、丢弃 jwks，token 端点永不验
            // client_assertion，接受它等于静默降级。待后续实现 client_assertion
            // 验签（jwks_uri / jwks 拉取与校验）后再放开。
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT,
                    "unsupported token_endpoint_auth_method");
        }

        String jwksUri = optionalString(document, "jwks_uri");
        Object jwksValue = document.get("jwks");
        String jwks = toJwksJson(jwksValue);
        if (jwksUri != null) {
            validateJwksUri(jwksUri);
        }

        List<String> grantTypes = optionalStringList(document, "grant_types");
        if (grantTypes == null) {
            // 缺省含 refresh_token：与 AS 元数据宣告及上游签发行为一致——refresh token
            // 换码时本来就会签发，缺省不给只会让已签发的 token 无法使用。注意这是对
            // RFC 7591 注册缺省（仅 authorization_code）的刻意偏离：省略字段的客户端
            // 刷新能力确实是新获得的，但它只让已持有对应 token 的客户端能刷新，
            // 不改变 token 的签发面。
            grantTypes = List.of("authorization_code", "refresh_token");
        } else {
            if (grantTypes.isEmpty() || !grantTypes.contains("authorization_code")
                    || !ALLOWED_GRANT_TYPES.containsAll(grantTypes)) {
                throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "invalid grant_types");
            }
        }

        List<String> responseTypes = optionalStringList(document, "response_types");
        if (responseTypes == null) {
            responseTypes = List.of("code");
        } else if (!List.of("code").equals(responseTypes)) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "response_types must be [code]");
        }

        String applicationType = optionalString(document, "application_type");
        if (applicationType == null) {
            applicationType = "web";
        } else if (!"web".equals(applicationType) && !"native".equals(applicationType)) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT,
                    "application_type must be web or native");
        }

        String clientUri = optionalString(document, "client_uri");
        if (clientUri != null) {
            validateHttpsAbsoluteUri(clientUri, "client_uri");
        }
        String logoUri = optionalString(document, "logo_uri");
        if (logoUri != null) {
            validateHttpsAbsoluteUri(logoUri, "logo_uri");
        }

        return new McpCimdMetadata(
                clientIdUrl,
                clientName,
                redirectUris,
                grantTypes,
                responseTypes,
                tokenEndpointAuthMethod,
                jwksUri,
                jwks,
                clientUri,
                logoUri,
                optionalString(document, "scope"),
                applicationType);
    }

    /**
     * 校验 jwks_uri：必须是 https 绝对 URI（与 client_id 同标准），且 host 必须公开。
     *
     * <p>jwks_uri 只做静态校验、不实际拉取，所以这里只做 host 的地址族判断：
     * 字面 IP 直接判，主机名做 DNS 解析判，与拉取层共用 {@link McpCimdSsrfGuard} 同一份检查。
     * DNS 解析失败时按无效文档拒绝（fail-closed），避免无法确认地址性质的 host 被放行。
     * 注意：本校验不看 allowPrivateAddresses——jwks_uri 指向内网地址一律拒绝。
     * （jwks 字段现阶段只做形状校验、不用于验签：private_key_jwt 已被上层直接拒绝，
     * 见 {@link #validateDocument}。）
     */
    private static void validateJwksUri(String jwksUri) throws McpCimdException {
        URI uri;
        try {
            uri = URI.create(jwksUri);
        } catch (IllegalArgumentException exception) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "jwks_uri is not a valid URI", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "jwks_uri scheme must be https");
        }
        if (uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT,
                    "jwks_uri must not contain a fragment or userinfo");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "jwks_uri must have a host");
        }
        McpCimdSsrfGuard.checkHostIsPublic(uri.getHost(), McpCimdError.INVALID_DOCUMENT);
    }

    /** client_uri / logo_uri 如提供，必须是 https 绝对 URI（无 fragment、无 userinfo、有 host）。 */
    private static void validateHttpsAbsoluteUri(String value, String field) throws McpCimdException {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, field + " is not a valid URI", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT,
                    field + " must be an https absolute URI");
        }
    }

    // ========== redirect_uri 规则 ==========

    /**
     * 校验一条 redirect_uri：必须是绝对 URI、无 fragment、无 userinfo；https 要求有 host；
     * http 只放行 loopback host；其他 scheme（自定义 / 私有 scheme）一律拒绝。
     *
     * <p>注意：本块对 CIMD 客户端的 redirect_uri 不放行自定义 scheme（CIMD 路径的
     * 客户端身份未经预注册，收紧处理）。
     */
    public static void validateRedirectUri(String redirectUri, Collection<String> localRedirectHosts)
            throws McpCimdException {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "redirect_uri is blank");
        }
        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (IllegalArgumentException exception) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "redirect_uri is not a valid URI", exception);
        }
        if (!uri.isAbsolute()) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "redirect_uri must be absolute");
        }
        if (uri.getRawFragment() != null) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "redirect_uri must not contain a fragment");
        }
        if (uri.getRawUserInfo() != null) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "redirect_uri must not contain userinfo");
        }
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            String host = extractHost(uri);
            if (host == null || host.isBlank()) {
                throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI,
                        "https redirect_uri must have a host");
            }
            return;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            String host = extractHost(uri);
            if (host != null && isLoopbackHost(host, localRedirectHosts)) {
                return;
            }
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI,
                    "http redirect_uri is only allowed for loopback hosts");
        }
        throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "unsupported redirect_uri scheme");
    }

    /**
     * 取 URI 的 host。java.net.URI 对 "127.0.0.1." 这类带尾点的数字型 IPv4 认不出 host
     *（getHost() 返回 null，连端口也取不到），这时从 raw authority 里手工拆出 host 部分；
     * 域名带尾点（localhost.）时 getHost() 正常返回，不走这条路。
     */
    private static String extractHost(URI uri) {
        String host = uri.getHost();
        if (host != null) {
            return host;
        }
        String authority = uri.getRawAuthority();
        if (authority == null) {
            return null;
        }
        int at = authority.lastIndexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            return close >= 0 ? hostPort.substring(0, close + 1) : null;
        }
        int colon = hostPort.lastIndexOf(':');
        return colon >= 0 ? hostPort.substring(0, colon) : hostPort;
    }

    /**
     * 是否 loopback host：localhost、127.0.0.0/8 整个段、::1，或配置里显式允许的本地 host。
     * DNS 尾点等价（localhost.、127.0.0.1. 也认）；127 段含整型写法（2130706433、0x7f000001）。
     */
    public static boolean isLoopbackHost(String host, Collection<String> localRedirectHosts) {
        if (host == null) {
            return false;
        }
        String normalized = normalizeHost(host);
        if ("localhost".equals(normalized) || "::1".equals(normalized)) {
            return true;
        }
        if (McpCimdSsrfGuard.isLoopbackIpv4Literal(normalized)) {
            return true;
        }
        if (localRedirectHosts == null) {
            return false;
        }
        for (String allowed : localRedirectHosts) {
            if (allowed != null && normalized.equals(normalizeHost(allowed))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        // DNS 尾点与无尾点等价，去掉再比对
        if (normalized.endsWith(".") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    // ========== 字段读取助手 ==========

    private static String requireNonBlankString(Map<String, Object> document, String key) throws McpCimdException {
        String value = optionalString(document, key);
        if (value == null || value.isBlank()) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, key + " must be a non-blank string");
        }
        return value;
    }

    /** 字段缺省返回 null；出现但不是字符串则拒绝（避免数字 / 布尔被静默转成字符串）。 */
    private static String optionalString(Map<String, Object> document, String key) throws McpCimdException {
        Object value = document.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, key + " must be a string");
        }
        String text = (String) value;
        return text.isBlank() ? null : text;
    }

    private static List<String> requireNonEmptyStringList(Map<String, Object> document, String key)
            throws McpCimdException {
        List<String> values = optionalStringList(document, key);
        if (values == null || values.isEmpty()) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, key + " must be a non-empty string list");
        }
        return values;
    }

    /** 字段缺省返回 null；出现但不是字符串列表则拒绝。 */
    private static List<String> optionalStringList(Map<String, Object> document, String key) throws McpCimdException {
        Object value = document.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, key + " must be a string list");
        }
        List<?> list = (List<?>) value;
        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, key + " must be a string list");
            }
        }
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) list;
        return result;
    }

    /** jwks 字段：字符串直接用；JSON 对象序列化回文本；其他类型拒绝。缺省返回 null。 */
    private static String toJwksJson(Object jwksValue) throws McpCimdException {
        if (jwksValue == null) {
            return null;
        }
        if (jwksValue instanceof String) {
            String text = (String) jwksValue;
            return text.isBlank() ? null : text;
        }
        if (jwksValue instanceof Map) {
            try {
                return OBJECT_MAPPER.writeValueAsString(jwksValue);
            } catch (JsonProcessingException exception) {
                throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "jwks is not serializable", exception);
            }
        }
        throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "jwks must be a string or an object");
    }

}
