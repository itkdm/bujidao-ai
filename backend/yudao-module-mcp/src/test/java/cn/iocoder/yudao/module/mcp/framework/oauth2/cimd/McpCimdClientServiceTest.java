package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCimdClientServiceTest {

    private HttpServer server;
    private int port;
    private McpOAuthProperties properties;
    private McpCimdClientService service;
    private AtomicInteger hits;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        hits = new AtomicInteger();
        properties = new McpOAuthProperties();
        properties.getCimd().setAllowPrivateAddresses(true);
        service = new McpCimdClientService(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String clientIdUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private String redirectUri() {
        return "http://127.0.0.1:" + port + "/callback";
    }

    private String validDocJson(String clientIdUrl) {
        return "{\"client_id\":\"" + clientIdUrl + "\","
                + "\"client_name\":\"Local Client\","
                + "\"redirect_uris\":[\"" + redirectUri() + "\"],"
                + "\"token_endpoint_auth_method\":\"none\"}";
    }

    private void respondJson(String path, String json) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    @Test
    void shouldResolveAndCache() throws Exception {
        String clientIdUrl = clientIdUrl("/client");
        respondJson("/client", validDocJson(clientIdUrl));
        server.start();

        McpCimdMetadata first = service.resolve(clientIdUrl, redirectUri());
        assertThat(first.clientName()).isEqualTo("Local Client");
        assertThat(first.tokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(hits.get()).isEqualTo(1);

        // 第二次命中缓存，不再发请求
        McpCimdMetadata second = service.resolve(clientIdUrl, redirectUri());
        assertThat(second).isEqualTo(first);
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void shouldRejectUnregisteredRedirectUriOnCacheHit() throws Exception {
        String clientIdUrl = clientIdUrl("/client");
        respondJson("/client", validDocJson(clientIdUrl));
        server.start();

        // 第一次用合法回调解析，建缓存
        McpCimdMetadata first = service.resolve(clientIdUrl, redirectUri());
        assertThat(first.clientName()).isEqualTo("Local Client");
        assertThat(hits.get()).isEqualTo(1);

        // 第二次用未注册回调解析同一 client_id：缓存命中也必须拒绝，且不得多发请求
        assertThatThrownBy(() -> service.resolve(clientIdUrl, "http://127.0.0.1:" + port + "/other"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void shouldRejectUnregisteredRedirectUri() throws Exception {
        String clientIdUrl = clientIdUrl("/client");
        respondJson("/client", validDocJson(clientIdUrl));
        server.start();

        assertThatThrownBy(() -> service.resolve(clientIdUrl, "http://127.0.0.1:" + port + "/other"))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_REDIRECT_URI);
    }

    @Test
    void shouldNotCacheFailedResolution() throws Exception {
        String clientIdUrl = clientIdUrl("/client");
        respondJson("/client", validDocJson(clientIdUrl));
        server.start();

        // 第一次：redirect_uri 不在文档里，失败
        assertThatThrownBy(() -> service.resolve(clientIdUrl, "http://127.0.0.1:" + port + "/other"))
                .isInstanceOf(McpCimdException.class);
        assertThat(hits.get()).isEqualTo(1);

        // 第二次：用正确 redirect_uri 应重新拉取（失败那次没入缓存），而不是直接返回失败
        McpCimdMetadata metadata = service.resolve(clientIdUrl, redirectUri());
        assertThat(metadata.clientName()).isEqualTo("Local Client");
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void shouldUseDefaultCimdWhenPropertiesCimdIsNull() {
        properties.setCimd(null);

        // 构造不 NPE；解析时用默认 cimd（allowPrivateAddresses=false），http 本地 URL 应被形状校验拒绝，
        // 而不是空指针
        McpCimdClientService nullSafeService = new McpCimdClientService(properties);
        assertThatCode(() -> new McpCimdClientService(properties)).doesNotThrowAnyException();
        assertThatThrownBy(() -> nullSafeService.resolve(
                clientIdUrl("/client"), redirectUri()))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_CLIENT_ID_URL);
    }

    @Test
    void shouldNotCacheInvalidDocument() throws Exception {
        String clientIdUrl = clientIdUrl("/bad-client");
        // 文档内 client_id 与拉取 URL 不一致
        respondJson("/bad-client", "{\"client_id\":\"https://other.example.com/x\","
                + "\"client_name\":\"Bad\","
                + "\"redirect_uris\":[\"" + redirectUri() + "\"]}");
        server.start();

        assertThatThrownBy(() -> service.resolve(clientIdUrl, redirectUri()))
                .isInstanceOf(McpCimdException.class)
                .extracting(exception -> ((McpCimdException) exception).getError())
                .isEqualTo(McpCimdError.INVALID_DOCUMENT);
        assertThat(hits.get()).isEqualTo(1);

        // 无效文档没入缓存：再调一次应重新拉取
        assertThatThrownBy(() -> service.resolve(clientIdUrl, redirectUri()))
                .isInstanceOf(McpCimdException.class);
        assertThat(hits.get()).isEqualTo(2);
    }

}
