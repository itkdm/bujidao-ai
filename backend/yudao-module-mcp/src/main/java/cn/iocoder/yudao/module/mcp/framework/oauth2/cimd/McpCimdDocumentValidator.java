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
    private static final String AUTH_METHOD_PRIVATE_KEY_JWT = "private_key_jwt";

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
        } else if (!AUTH_METHOD_NONE.equals(tokenEndpointAuthMethod)
                && !AUTH_METHOD_PRIVATE_KEY_JWT.equals(tokenEndpointAuthMethod)) {
            // shared-secret 类方法（client_secret_basic / client_secret_post / client_secret_jwt 等）
            // 要求服务端保管客户端密钥，与 CIMD“客户端自带身份、无预注册密钥”的模型冲突，一律拒绝。
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT,
                    "unsupported token_endpoint_auth_method");
        }

        String jwksUri = optionalString(document, "jwks_uri");
        Object jwksValue = document.get("jwks");
        String jwks = toJwksJson(jwksValue);
        if (AUTH_METHOD_PRIVATE_KEY_JWT.equals(tokenEndpointAuthMethod)
                && (jwksUri == null || jwksUri.isBlank()) && jwks == null) {
            throw new McpCimdException(McpCimdError.INVALID_DOCUMENT,
                    "private_key_jwt requires jwks_uri or jwks");
        }

        List<String> grantTypes = optionalStringList(document, "grant_types");
        if (grantTypes == null) {
            grantTypes = List.of("authorization_code");
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
                optionalString(document, "client_uri"),
                optionalString(document, "logo_uri"),
                optionalString(document, "scope"),
                applicationType);
    }

    // ========== redirect_uri 规则 ==========

    /**
     * 校验一条 redirect_uri：必须是绝对 URI、无 fragment；https 一律放行；
     * http 只放行 loopback host；其他 scheme（自定义 / 私有 scheme）一律拒绝。
     *
     * <p>注意：本块对 CIMD 客户端的 redirect_uri 不放行自定义 scheme，即使
     * McpOAuthProperties.allowPrivateUseUriSchemeRedirects=true（那是 DCR 注册路径的开关，
     * CIMD 路径的客户端身份未经预注册，收紧处理）。
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
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            if (uri.getHost() != null && isLoopbackHost(uri.getHost(), localRedirectHosts)) {
                return;
            }
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI,
                    "http redirect_uri is only allowed for loopback hosts");
        }
        throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI, "unsupported redirect_uri scheme");
    }

    /**
     * 是否 loopback host：localhost、127.0.0.1、::1，或配置里显式允许的本地 host。
     */
    public static boolean isLoopbackHost(String host, Collection<String> localRedirectHosts) {
        if (host == null) {
            return false;
        }
        String normalized = normalizeHost(host);
        if ("localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized)) {
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
