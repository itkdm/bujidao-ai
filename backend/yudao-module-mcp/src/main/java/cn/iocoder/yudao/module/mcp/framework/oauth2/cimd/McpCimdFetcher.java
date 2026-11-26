package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
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
 *   <li>SSRF 防护在发请求前做 DNS 解析层检查：{@link McpCimdSsrfGuard#checkHostIsPublic(String, McpCimdError)}
 *       解析出的<b>任一</b>地址非公开即拒绝，不能只看 URL 字符串
 *       （主机名可能解析到 169.254.169.254 这类地址）；谓词覆盖的地址族清单见该类注释；</li>
 *   <li>body 按流式限长读取，超限即拒，不把无限大的响应先读进内存；</li>
 *   <li>异常信息只写原因类别与状态码，绝不含 body 原文；</li>
 *   <li>限流在发请求前做：单 host / 全局 60 秒窗口 + 并发上限，超限抛
 *       {@link McpCimdError#RATE_LIMITED}，由解析器转成 OAuth {@code invalid_client}，
 *       这里不记日志（解析器对每次 CIMD 失败只记一条 warn，避免重复）。</li>
 * </ul>
 *
 * @author bujidao
 */
public class McpCimdFetcher {

    private static final String ACCEPT_JSON = "application/json";
    private static final Pattern MAX_AGE_PATTERN =
            // max-age 的值允许带可选引号：max-age="3600" / max-age="0" 都要认，
            // 否则 lookingAt() 因引号失败、走默认 TTL，max-age="0" 的文档本应不缓存
            Pattern.compile("max-age\\s*=\\s*\"?(\\d+)\"?", Pattern.CASE_INSENSITIVE);

    private final Duration fetchTimeout;
    private final int maxDocumentBytes;
    private final boolean allowPrivateAddresses;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    /** 限流器：Spring 注入共用 Bean；编程式构造时用默认配置自建一个，不为 null。 */
    private final McpOAuthRateLimiter rateLimiter;

    public McpCimdFetcher(McpOAuthProperties.Cimd config, ObjectMapper objectMapper) {
        this(config, objectMapper, new McpOAuthRateLimiter(new McpOAuthProperties.RateLimit()));
    }

    public McpCimdFetcher(McpOAuthProperties.Cimd config, ObjectMapper objectMapper,
                          McpOAuthRateLimiter rateLimiter) {
        this(config != null ? config.getFetchTimeout() : null,
                config != null ? config.getMaxDocumentBytes() : 0,
                config != null && config.isAllowPrivateAddresses(),
                objectMapper, rateLimiter);
    }

    public McpCimdFetcher(Duration fetchTimeout, int maxDocumentBytes, boolean allowPrivateAddresses,
                          ObjectMapper objectMapper) {
        this(fetchTimeout, maxDocumentBytes, allowPrivateAddresses, objectMapper,
                new McpOAuthRateLimiter(new McpOAuthProperties.RateLimit()));
    }

    public McpCimdFetcher(Duration fetchTimeout, int maxDocumentBytes, boolean allowPrivateAddresses,
                          ObjectMapper objectMapper, McpOAuthRateLimiter rateLimiter) {
        // 编程式构造时字段可能为 null / 非法：用默认值兜底，不 NPE（绑定层有 @NotNull / @Min 校验，
        // 正常启动走不到这里）
        this.fetchTimeout = fetchTimeout != null ? fetchTimeout : Duration.ofSeconds(3);
        this.maxDocumentBytes = maxDocumentBytes > 0 ? maxDocumentBytes : 5120;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.rateLimiter = rateLimiter != null
                ? rateLimiter : new McpOAuthRateLimiter(new McpOAuthProperties.RateLimit());
        this.httpClient = HttpClient.newBuilder()
                // 不跟随重定向：重定向目标不受校验约束，绝不能自动跟进
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(this.fetchTimeout)
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
        // 2. 限流计数（在 SSRF 检查与发请求之前，先挡住反复拉取）：单 host 窗口 + 全局窗口。
        //    超限抛 RATE_LIMITED：解析器会把它转成 OAuth invalid_client（400），不产生 500。
        //    这里不记日志——解析器对每次 CIMD 失败只记一条 warn（含 host 与错误类别），
        //    在这里再记就重复了。
        if (rateLimiter.isEnabled()
                && !rateLimiter.tryAcquireCimdFetch(uri.getHost())) {
            throw new McpCimdException(McpCimdError.RATE_LIMITED, "CIMD fetch rate limited");
        }
        // 3. SSRF 防护：DNS 解析层检查，任一解析地址命中非公开范围即拒绝。
        //    注意：这里故意用 getAllByName 而不是只看 URL 字符串，防止主机名解析到内网地址。
        //    TOCTOU 说明：解析与建连之间 DNS 记录理论上可能变化（DNS rebinding），
        //    彻底防御需要自定义解析 / 连接层；本块先做到“解析层拦截已知非公开地址”，
        //    rebinding 加固留给后续安全加固 Block。
        if (!allowPrivateAddresses) {
            McpCimdSsrfGuard.checkHostIsPublic(uri.getHost(), McpCimdError.FETCH_FAILED);
        }
        // 4. 只发 GET，Accept: application/json。
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(fetchTimeout)
                .header("Accept", ACCEPT_JSON)
                .GET()
                .build();
        // 5. 并发槽覆盖“发请求 + 读 body”全程，结束即释放；没拿到槽直接按限流拒绝、不发请求。
        boolean slotAcquired = false;
        if (rateLimiter.isEnabled()) {
            if (!rateLimiter.tryAcquireCimdSlot()) {
                throw new McpCimdException(McpCimdError.RATE_LIMITED, "too many concurrent CIMD fetches");
            }
            slotAcquired = true;
        }
        try {
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
                if (!isJsonContentType(contentType)) {
                    throw new McpCimdException(McpCimdError.INVALID_CONTENT_TYPE,
                            "Content-Type must be application/json");
                }
                byte[] bytes = readUpTo(body, maxDocumentBytes);
                Map<String, Object> document;
                try {
                    document = objectMapper.readValue(bytes, new TypeReference<Map<String, Object>>() {
                    });
                } catch (IOException exception) {
                    // 只写“解析失败”这一类别，不把 body 原文放进异常信息；
                    // cause 链里也只挂 Jackson 异常的类型名，不挂原始 cause：
                    // Jackson 的解析错误信息可能回显 body 片段，挂进 cause 会在日志打印堆栈时把攻击者可控文本打出来。
                    throw new McpCimdException(McpCimdError.INVALID_DOCUMENT, "document is not valid JSON",
                            new IOException(exception.getClass().getSimpleName()));
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
        } finally {
            if (slotAcquired) {
                rateLimiter.releaseCimdSlot();
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

    /**
     * Content-Type 是否为 JSON：按 ';' 切出 media-type 后精确比对 application/json，
     * 或接受 +json 后缀类型（如 application/hal+json）。
     * 不能用 contains("application/json")：application/json-patch、application/jsonx
     * 这类“包含但不是 JSON”的类型会被误放行。
     */
    static boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim();
        return ACCEPT_JSON.equalsIgnoreCase(mediaType)
                || mediaType.toLowerCase(Locale.ROOT).endsWith("+json");
    }

    /** 解析 Cache-Control 的 max-age，解析不到返回 null（由缓存层用默认 TTL）。 */
    static Long parseMaxAge(String cacheControl) {
        if (cacheControl == null || cacheControl.isBlank()) {
            return null;
        }
        String[] directives = cacheControl.split(",");
        // 第一遍：任一 no-store / no-cache 即不缓存。不能在第一遍里遇到 max-age 就
        // 立即 return——"max-age=3600, no-store" 里 no-store 写在后面也会被漏掉。
        for (String rawDirective : directives) {
            String directive = rawDirective.trim();
            if ("no-store".equalsIgnoreCase(directive) || "no-cache".equalsIgnoreCase(directive)) {
                // 明确要求不缓存：返回 0，缓存层据此不入缓存
                return 0L;
            }
        }
        // 第二遍：再找 max-age
        for (String rawDirective : directives) {
            String directive = rawDirective.trim();
            Matcher matcher = MAX_AGE_PATTERN.matcher(directive);
            // 必须从指令开头匹配：x-max-age 这类名字相近的指令不能误解析；
            // 大小写不敏感：Max-Age / MAX-AGE 都认
            if (matcher.lookingAt()) {
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 拉取结果：解析后的文档 + 响应 Cache-Control 的 max-age（秒，无缓存头时为 null）。
     */
    public record FetchedDocument(Map<String, Object> document, Long maxAgeSeconds) {
    }

}
