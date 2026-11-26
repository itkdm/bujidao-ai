package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

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
        }
    }

}
