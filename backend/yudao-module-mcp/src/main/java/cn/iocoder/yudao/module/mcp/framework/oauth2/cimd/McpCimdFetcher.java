package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CIMD 文档拉取器：用 JDK {@link HttpClient} 对 client_id URL 做一次 GET。
 *
 * <p>安全要点：
 * <ul>
 *   <li>不跟随重定向（NEVER）：重定向目标未经校验，交给后续 Block 的授权端点显式处理，拉取层不自动跟进；</li>
 *   <li>SSRF 防护在发请求前做 DNS 解析层检查：{@link InetAddress#getAllByName(String)} 解析出的
 *       <b>任一</b>地址是回环 / 内网 / 链路本地 / 组播 / 未指定地址即拒绝，不能只看 URL 字符串
 *       （主机名可能解析到 169.254.169.254 这类地址）；</li>
 *   <li>body 按流式限长读取，超限即拒，不把无限大的响应先读进内存；</li>
 *   <li>异常信息只写原因类别与状态码，绝不含 body 原文。</li>
 * </ul>
 *
 * @author bujidao
 */
public class McpCimdFetcher {

    private static final String ACCEPT_JSON = "application/json";
    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age\\s*=\\s*(\\d+)");

    private final Duration fetchTimeout;
    private final int maxDocumentBytes;
    private final boolean allowPrivateAddresses;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpCimdFetcher(McpOAuthProperties.Cimd config, ObjectMapper objectMapper) {
        this(config.getFetchTimeout(), config.getMaxDocumentBytes(), config.isAllowPrivateAddresses(), objectMapper);
    }

    public McpCimdFetcher(Duration fetchTimeout, int maxDocumentBytes, boolean allowPrivateAddresses,
                          ObjectMapper objectMapper) {
        this.fetchTimeout = fetchTimeout;
        this.maxDocumentBytes = maxDocumentBytes;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                // 不跟随重定向：重定向目标不受校验约束，绝不能自动跟进
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(fetchTimeout)
                .build();
    }

    /**
     * 拉取并解析 CIMD 文档。
     *
     * @param clientIdUrl 客户端提供的 client_id URL
     * @return 解析后的文档与响应 Cache-Control 里的 max-age（秒，无缓存头时为 null）
     */
    public FetchedDocument fetch(String clientIdUrl) throws McpCimdException {
        // 1. 先校验 URL 形状。allowPrivateAddresses=true 时放行 http scheme，
        //    仅供本地开发 / 测试用 HttpServer 模拟，生产必须保持 false（只允许 https）。
        URI uri = McpCimdDocumentValidator.validateClientIdUrl(clientIdUrl, allowPrivateAddresses);
        // 2. SSRF 防护：DNS 解析层检查，任一解析地址命中私有范围即拒绝。
        //    注意：这里故意用 getAllByName 而不是只看 URL 字符串，防止主机名解析到内网地址。
        //    TOCTOU 说明：解析与建连之间 DNS 记录理论上可能变化（DNS rebinding），
        //    彻底防御需要自定义解析 / 连接层；本块先做到“解析层拦截已知私有地址”，
        //    rebinding 加固留给后续安全加固 Block。
        if (!allowPrivateAddresses) {
            checkNotPrivateAddress(uri.getHost());
        }
        // 3. 只发 GET，Accept: application/json。
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(fetchTimeout)
                .header("Accept", ACCEPT_JSON)
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException exception) {
            throw new McpCimdException(McpCimdError.FETCH_TIMEOUT, "fetch timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new McpCimdException(McpCimdError.FETCH_FAILED, "fetch interrupted", exception);
        } catch (IOException exception) {
            throw new McpCimdException(McpCimdError.FETCH_FAILED, "fetch failed", exception);
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new McpCimdException(McpCimdError.INVALID_STATUS,
                        "unexpected HTTP status: " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).contains(ACCEPT_JSON)) {
                throw new McpCimdException(McpCimdError.INVALID_CONTENT_TYPE,
                        "Content-Type must contain application/json");
            }
            byte[] bytes = readUpTo(body, maxDocumentBytes);
            Map<String, Object> document;
            try {
                document = objectMapper.readValue(bytes, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException exception) {
                // 只写“解析失败”这一类别，不把 body 原文放进异常信息
                throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "document is not valid JSON", exception);
            }
            if (document == null) {
                throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "document is not a JSON object");
            }
            Long maxAgeSeconds = parseMaxAge(response.headers().firstValue("Cache-Control").orElse(null));
            return new FetchedDocument(document, maxAgeSeconds);
        } catch (McpCimdException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new McpCimdException(McpCimdError.FETCH_FAILED, "failed to read response body", exception);
        }
    }

    /**
     * DNS 解析层 SSRF 检查：任一解析地址是回环 / 内网 / 链路本地 / 组播 / 未指定地址即拒绝。
     */
    private void checkNotPrivateAddress(String host) throws McpCimdException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new McpCimdException(McpCimdError.FETCH_FAILED, "failed to resolve host", exception);
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isMulticastAddress()
                    || address.isAnyLocalAddress()) {
                throw new McpCimdException(McpCimdError.SSRF_BLOCKED,
                        "resolved address is not a public address");
            }
        }
    }

    /** 流式限长读取：读到 maxBytes + 1 即停并拒绝，不把超大响应读进内存。 */
    private byte[] readUpTo(InputStream input, int maxBytes) throws McpCimdException, IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new McpCimdException(McpCimdError.DOCUMENT_TOO_LARGE,
                        "document exceeds max size: " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** 解析 Cache-Control 的 max-age，解析不到返回 null（由缓存层用默认 TTL）。 */
    static Long parseMaxAge(String cacheControl) {
        if (cacheControl == null || cacheControl.isBlank()) {
            return null;
        }
        Matcher matcher = MAX_AGE_PATTERN.matcher(cacheControl);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 拉取结果：解析后的文档 + 响应 Cache-Control 的 max-age（秒，无缓存头时为 null）。
     */
    public record FetchedDocument(Map<String, Object> document, Long maxAgeSeconds) {
    }

}
