package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdDocumentValidator;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * MCP OAuth redirect_uri 匹配器：注册过的 URI 精确匹配优先，回环地址允许任意端口。
 *
 * <p>背景（RFC 8252 §7.3）：原生应用的回环重定向 URI，授权服务器必须允许任意端口——
 * 桌面客户端每次启动都可能占一个不同的随机回环端口，注册时登记的固定端口下次启动
 * 就对不上。精确匹配仍是默认规则，放宽只针对回环地址，且只忽略端口，不忽略
 * scheme / host / path / query。
 *
 * <p>匹配规则：
 * <ul>
 *     <li>精确相等（字符串逐字相同）直接通过；</li>
 *     <li>否则仅当请求 URI 是 http/https 回环地址时，允许端口不同：scheme、host
 *     （归一化：小写、去 IPv6 方括号、去尾点）、path、query 均相同即通过；</li>
 *     <li>其余情况（非回环换端口、换 host、换 path、换 query、带 fragment/userinfo、
 *     非绝对 URI、解析失败）一律不匹配。</li>
 * </ul>
 *
 * <p>回环判定复用 {@link McpCimdDocumentValidator#isLoopbackHost(String, Collection)}；
 * host 提取与归一化与该类的同款处理一致（URI 对带尾点的数字型 IPv4 认不出 host）。
 *
 * @author bujidao
 */
public class McpOAuthRedirectUriMatcher {

    /**
     * 请求的 redirect_uri 是否与该客户端注册过的 URI 之一匹配。
     *
     * @param requestedUri       本次请求带的 redirect_uri
     * @param registeredUris     客户端注册过的 redirect_uri 列表
     * @param localRedirectHosts 允许的本地 host（见 McpOAuthProperties.localRedirectHosts）
     * @return 匹配返回 true
     */
    public static boolean matches(String requestedUri, Collection<String> registeredUris,
                                  Collection<String> localRedirectHosts) {
        if (StrUtil.isBlank(requestedUri) || registeredUris == null || registeredUris.isEmpty()) {
            return false;
        }
        if (registeredUris.contains(requestedUri)) {
            return true;
        }
        URI requested = parse(requestedUri);
        if (requested == null || !requested.isAbsolute()
                || requested.getRawFragment() != null || requested.getRawUserInfo() != null) {
            return false;
        }
        String scheme = requested.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = extractHost(requested);
        if (host == null || !McpCimdDocumentValidator.isLoopbackHost(host, localRedirectHosts)) {
            return false;
        }
        for (String registered : registeredUris) {
            if (registered == null) {
                continue;
            }
            URI candidate = parse(registered);
            if (candidate == null || !candidate.isAbsolute()
                    || candidate.getRawFragment() != null || candidate.getRawUserInfo() != null) {
                continue;
            }
            if (!scheme.equalsIgnoreCase(candidate.getScheme())) {
                continue;
            }
            String candidateHost = extractHost(candidate);
            if (candidateHost == null || !normalizeHost(host).equals(normalizeHost(candidateHost))) {
                continue;
            }
            if (!Objects.equals(requested.getRawPath(), candidate.getRawPath())) {
                continue;
            }
            if (!Objects.equals(requested.getRawQuery(), candidate.getRawQuery())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    /**
     * 取 URI 的 host。与 McpCimdDocumentValidator.extractHost 同款处理：
     * java.net.URI 对 "127.0.0.1." 这类带尾点的数字型 IPv4 认不出 host
     * （getHost() 返回 null，连端口也取不到），这时从 raw authority 里手工拆出 host 部分；
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
     * host 归一化：小写、去 IPv6 方括号、去尾点（DNS 尾点与无尾点等价）。
     */
    private static String normalizeHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
