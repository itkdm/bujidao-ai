package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCimdFetcherTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private McpCimdFetcher strictFetcher() {
        return new McpCimdFetcher(TIMEOUT, 5120, false, new ObjectMapper());
    }

    private McpCimdFetcher localFetcher() {
        return new McpCimdFetcher(TIMEOUT, 5120, true, new ObjectMapper());
    }

    private String localUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void respondJson(String path, String json, String cacheControl) {
        server.createContext(path, exchange -> {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (cacheControl != null) {
                exchange.getResponseHeaders().set("Cache-Control", cacheControl);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    // ========== SSRF 防护：发请求前就拒绝，不产生真实请求 ==========

    @Test
    void shouldRejectLoopbackAddressBeforeRequest() {
        assertThatThrownBy(() -> strictFetcher().fetch("https://127.0.0.1:8443/client"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.SSRF_BLOCKED);
    }

    @Test
    void shouldRejectIpv6LoopbackAddressBeforeRequest() {
        assertThatThrownBy(() -> strictFetcher().fetch("https://[::1]:8443/client"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.SSRF_BLOCKED);
    }

    @Test
    void shouldRejectPrivateNetworkAddressBeforeRequest() {
        assertThatThrownBy(() -> strictFetcher().fetch("https://10.0.0.1/client"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.SSRF_BLOCKED);
    }

    @Test
    void shouldRejectPrivateNetworkRangesBeforeRequest() {
        // 172.16.0.0/12 与 192.168.0.0/16：此前拉取层无新增用例，补回归覆盖（含段边界）
        for (String url : List.of(
                "https://172.16.0.1/client",
                "https://172.31.255.255/client",
                "https://192.168.0.1/client",
                "https://192.168.255.255/client")) {
            assertThatThrownBy(() -> strictFetcher().fetch(url))
                    .as("SSRF should block " + url)
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.SSRF_BLOCKED);
        }
    }

    @Test
    void shouldRejectUniqueLocalIpv6AddressesBeforeRequest() {
        // fc00::/7（RFC 4193 ULA）：JDK 的 isSiteLocalAddress 只认已废弃的 fec0::/10，
        // 不显式补这一段会被当成公开地址放行
        for (String url : List.of(
                "https://[fd00::1]/client",
                "https://[fc00::1]/client")) {
            assertThatThrownBy(() -> strictFetcher().fetch(url))
                    .as("SSRF should block " + url)
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.SSRF_BLOCKED);
        }
    }

    @Test
    void shouldRejectLinkLocalAddressBeforeRequest() {
        // 169.254.169.254 是云厂商元数据地址，必须拦
        assertThatThrownBy(() -> strictFetcher().fetch("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.SSRF_BLOCKED);
    }

    @Test
    void shouldRejectHostnameResolvingToLoopback() {
        assertThatThrownBy(() -> strictFetcher().fetch("https://localhost:8443/client"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.SSRF_BLOCKED);
    }

    @Test
    void shouldRejectCgnatAndReservedRangesBeforeRequest() {
        for (String url : List.of(
                "https://100.64.0.1/client",
                "https://100.127.255.255/client",
                "https://192.0.0.1/client",
                "https://192.0.0.170/client",
                "https://198.18.0.1/client",
                "https://198.19.255.255/client",
                "https://240.0.0.1/client",
                "https://255.255.255.255/client",
                // IPv4 映射 IPv6 地址归一化成 IPv4 后再判
                "https://[::ffff:10.0.0.1]/client",
                "https://[::ffff:127.0.0.1]/client",
                // 十进制 / 十六进制整型 IP 写法
                "https://2130706433/client",
                "https://0x7f000001/client")) {
            assertThatThrownBy(() -> strictFetcher().fetch(url))
                    .as("SSRF should block " + url)
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.SSRF_BLOCKED);
        }
    }

    @Test
    void shouldRejectHttpUrlBeforeRequest() {
        // http scheme 在生产模式下形状校验即失败，根本到不了发请求那一步
        assertThatThrownBy(() -> strictFetcher().fetch("http://127.0.0.1:8080/client"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    // ========== happy-path：本地 HttpServer + allowPrivateAddresses=true ==========

    @Test
    void shouldFetchAndParseDocument() throws Exception {
        String clientIdUrl = localUrl("/client");
        String json = "{\"client_id\":\"" + clientIdUrl + "\","
                + "\"client_name\":\"Local Client\","
                + "\"redirect_uris\":[\"http://127.0.0.1:" + port + "/callback\"]}";
        respondJson("/client", json, "public, max-age=3600");
        server.start();

        McpCimdFetcher.FetchedDocument fetched = localFetcher().fetch(clientIdUrl);

        assertThat(fetched.document().get("client_id")).isEqualTo(clientIdUrl);
        assertThat(fetched.document().get("client_name")).isEqualTo("Local Client");
        assertThat(fetched.maxAgeSeconds()).isEqualTo(3600L);
    }

    @Test
    void shouldReturnNullMaxAgeWithoutCacheControl() throws Exception {
        String clientIdUrl = localUrl("/client");
        respondJson("/client", "{\"client_id\":\"" + clientIdUrl + "\"}", null);
        server.start();

        McpCimdFetcher.FetchedDocument fetched = localFetcher().fetch(clientIdUrl);

        assertThat(fetched.maxAgeSeconds()).isNull();
    }

    @Test
    void shouldReturnZeroMaxAgeForNoStore() throws Exception {
        String clientIdUrl = localUrl("/client");
        respondJson("/client", "{\"client_id\":\"" + clientIdUrl + "\"}", "no-store");
        server.start();

        McpCimdFetcher.FetchedDocument fetched = localFetcher().fetch(clientIdUrl);

        // no-store 返回 0，缓存层据此不入缓存
        assertThat(fetched.maxAgeSeconds()).isEqualTo(0L);
    }

    // ========== Cache-Control 解析 ==========

    @Test
    void shouldParseMaxAgeCaseInsensitively() {
        assertThat(McpCimdFetcher.parseMaxAge("public, max-age=3600")).isEqualTo(3600L);
        assertThat(McpCimdFetcher.parseMaxAge("public, Max-Age=60")).isEqualTo(60L);
        assertThat(McpCimdFetcher.parseMaxAge("public, MAX-AGE=30")).isEqualTo(30L);
        assertThat(McpCimdFetcher.parseMaxAge("max-age = 120")).isEqualTo(120L);
    }

    @Test
    void shouldReturnZeroMaxAgeForNoStoreOrNoCache() {
        assertThat(McpCimdFetcher.parseMaxAge("no-store")).isEqualTo(0L);
        assertThat(McpCimdFetcher.parseMaxAge("public, no-cache")).isEqualTo(0L);
        assertThat(McpCimdFetcher.parseMaxAge("No-Store")).isEqualTo(0L);
        assertThat(McpCimdFetcher.parseMaxAge("NO-CACHE, max-age=3600")).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroMaxAgeWhenNoStoreFollowsMaxAge() {
        // no-store 写在 max-age 后面也不能漏：旧实现遇到 max-age 立即 return，
        // 后面的指令不再看，"max-age=3600, no-store" 会错返回 3600
        assertThat(McpCimdFetcher.parseMaxAge("max-age=3600, no-store")).isEqualTo(0L);
        assertThat(McpCimdFetcher.parseMaxAge("max-age=3600, no-cache")).isEqualTo(0L);
        assertThat(McpCimdFetcher.parseMaxAge("public, max-age=60, No-Store")).isEqualTo(0L);
    }

    @Test
    void shouldParseQuotedMaxAge() {
        // 带引号的 max-age：旧实现因引号导致 lookingAt() 失败返回 null、走默认 TTL，
        // max-age="0" 的文档本应不缓存
        assertThat(McpCimdFetcher.parseMaxAge("max-age=\"3600\"")).isEqualTo(3600L);
        assertThat(McpCimdFetcher.parseMaxAge("public, max-age=\"60\"")).isEqualTo(60L);
        assertThat(McpCimdFetcher.parseMaxAge("max-age=\"0\"")).isEqualTo(0L);
    }

    @Test
    void shouldNotParseLookalikeMaxAgeDirective() {
        assertThat(McpCimdFetcher.parseMaxAge("x-max-age=3600")).isNull();
        assertThat(McpCimdFetcher.parseMaxAge("public")).isNull();
        assertThat(McpCimdFetcher.parseMaxAge(null)).isNull();
        assertThat(McpCimdFetcher.parseMaxAge("  ")).isNull();
    }

    // ========== 超时 ==========

    @Test
    void shouldTimeoutOnSlowResponse() {
        server.createContext("/client", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        McpCimdFetcher fetcher = new McpCimdFetcher(Duration.ofMillis(200), 5120, true, new ObjectMapper());

        assertThatThrownBy(() -> fetcher.fetch(localUrl("/client")))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.FETCH_TIMEOUT);
    }

    // ========== 响应校验 ==========

    @Test
    void shouldRejectNon200Status() throws Exception {
        server.createContext("/client", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> localFetcher().fetch(localUrl("/client")))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_STATUS);
    }

    @Test
    void shouldNotFollowRedirect() throws Exception {
        server.createContext("/client", exchange -> {
            exchange.getResponseHeaders().set("Location", localUrl("/other"));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> localFetcher().fetch(localUrl("/client")))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_STATUS);
    }

    @Test
    void shouldRejectNonJsonContentType() throws Exception {
        server.createContext("/client", exchange -> {
            byte[] bytes = "<html></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        assertThatThrownBy(() -> localFetcher().fetch(localUrl("/client")))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CONTENT_TYPE);
    }

    @Test
    void shouldAcceptJsonContentTypeWithCharset() throws Exception {
        server.createContext("/client", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        McpCimdFetcher.FetchedDocument fetched = localFetcher().fetch(localUrl("/client"));

        assertThat(fetched.document()).isEmpty();
    }

    @Test
    void shouldAcceptJsonSuffixContentType() throws Exception {
        server.createContext("/client", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/hal+json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        McpCimdFetcher.FetchedDocument fetched = localFetcher().fetch(localUrl("/client"));

        assertThat(fetched.document()).isEmpty();
    }

    @Test
    void shouldRejectLookalikeJsonContentType() throws Exception {
        // 旧的 contains("application/json") 会误放行这些类型
        AtomicReference<String> contentType = new AtomicReference<>("application/json-patch");
        server.createContext("/client", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType.get());
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        for (String value : List.of("application/json-patch", "application/jsonx")) {
            contentType.set(value);
            assertThatThrownBy(() -> localFetcher().fetch(localUrl("/client")))
                    .as("Content-Type " + value + " should be rejected")
                    .isInstanceOf(McpCimdException.class)
                    .extracting(exception -> ((McpCimdException) exception).getError())
                    .isEqualTo(McpCimdError.INVALID_CONTENT_TYPE);
        }
    }

    @Test
    void shouldRejectOversizedDocument() throws Exception {
        // 用小上限（100 字节）验证限长逻辑，不必真的发 5KB
        McpCimdFetcher fetcher = new McpCimdFetcher(TIMEOUT, 100, true, new ObjectMapper());
        respondJson("/client", "{\"client_id\":\"" + localUrl("/client") + "\",\"padding\":\""
                + "x".repeat(200) + "\"}", null);
        server.start();

        assertThatThrownBy(() -> fetcher.fetch(localUrl("/client")))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.DOCUMENT_TOO_LARGE);
    }

    // ========== 限流 ==========

    @Test
    void shouldRejectFetchWhenCimdRateLimited() throws Exception {
        String clientIdUrl = localUrl("/client");
        String otherUrl = localUrl("/other");
        respondJson("/client", "{\"client_id\":\"" + clientIdUrl + "\"}", null);
        respondJson("/other", "{\"client_id\":\"" + otherUrl + "\"}", null);
        server.start();

        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setCimdPerHostPerMinute(1);
        McpCimdFetcher fetcher = new McpCimdFetcher(TIMEOUT, 5120, true, new ObjectMapper(),
                new McpOAuthRateLimiter(config));

        McpCimdFetcher.FetchedDocument fetched = fetcher.fetch(clientIdUrl);
        assertThat(fetched.document().get("client_id")).isEqualTo(clientIdUrl);
        // 同一 host 第二次拉取：单 host 窗口已满，直接抛 RATE_LIMITED，不发请求
        assertThatThrownBy(() -> fetcher.fetch(otherUrl))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.RATE_LIMITED);
    }

    @Test
    void shouldRejectFetchWhenCimdSlotsExhausted() throws Exception {
        String clientIdUrl = localUrl("/client");
        respondJson("/client", "{\"client_id\":\"" + clientIdUrl + "\"}", null);
        server.start();

        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setCimdMaxConcurrentFetches(1);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config);
        McpCimdFetcher fetcher = new McpCimdFetcher(TIMEOUT, 5120, true, new ObjectMapper(), limiter);

        assertThat(limiter.tryAcquireCimdSlot()).isTrue();
        // 并发槽被占满：不发请求，直接抛 RATE_LIMITED
        assertThatThrownBy(() -> fetcher.fetch(clientIdUrl))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.RATE_LIMITED);
        // 释放槽后恢复
        limiter.releaseCimdSlot();
        assertThat(fetcher.fetch(clientIdUrl).document().get("client_id")).isEqualTo(clientIdUrl);
    }

    @Test
    void shouldRejectInvalidJsonWithoutLeakingBody() throws Exception {
        String body = "{not json SECRET_MARKER_12345";
        respondJson("/client", body, null);
        server.start();

        try {
            localFetcher().fetch(localUrl("/client"));
            org.junit.jupiter.api.Assertions.fail("expected McpCimdException");
        } catch (McpCimdException exception) {
            assertThat(exception.getError()).isEqualTo(McpCimdError.INVALID_DOCUMENT);
            // 异常信息绝不能含攻击者可控的 body 原文
            assertThat(exception.getMessage()).doesNotContain("SECRET_MARKER_12345");
            // cause 链里也只挂 Jackson 异常的类型名，不能含 body 原文
            assertThat(exception.getCause()).isNotNull();
            assertThat(exception.getCause().getMessage()).doesNotContain("SECRET_MARKER_12345");
            assertThat(exception.getCause().getMessage()).isEqualTo("JsonParseException");
        }
    }

}
