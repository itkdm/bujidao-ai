package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpCimdMetadataCacheTest {

    private static McpCimdMetadata metadata(String clientId) {
        return new McpCimdMetadata(clientId, "Test Client",
                List.of("https://client.example.com/callback"),
                List.of("authorization_code"), List.of("code"), "none",
                null, null, null, null, null, "web");
    }

    @Test
    void shouldHitCachedDocument() {
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofHours(24), Duration.ofHours(1), 1000);

        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), null);

        assertThat(cache.get("https://client.example.com/a")).isNotNull();
        assertThat(cache.get("https://client.example.com/a").clientName()).isEqualTo("Test Client");
        assertThat(cache.get("https://client.example.com/missing")).isNull();
    }

    @Test
    void shouldExpireByDefaultTtlWithoutCacheHeader() throws InterruptedException {
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofHours(24), Duration.ofMillis(50), 1000);

        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), null);
        assertThat(cache.get("https://client.example.com/a")).isNotNull();

        Thread.sleep(150);

        // 过期条目视为未命中并删除
        assertThat(cache.get("https://client.example.com/a")).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    void shouldRespectMaxAgeFromResponse() throws InterruptedException {
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofHours(24), Duration.ofHours(1), 1000);

        // max-age=3600：150ms 后仍命中
        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), 3600L);
        Thread.sleep(150);
        assertThat(cache.get("https://client.example.com/a")).isNotNull();

        // max-age=0：立即过期，不入缓存
        cache.put("https://client.example.com/b", metadata("https://client.example.com/b"), 0L);
        assertThat(cache.get("https://client.example.com/b")).isNull();
    }

    @Test
    void shouldCapTtlAtHardMaxAge() throws InterruptedException {
        // 硬上限 300ms，响应 max-age=86400 也只能缓存 300ms；睡 600ms（2 倍裕量）后应过期
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofMillis(300), Duration.ofHours(1), 1000);

        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), 86400L);
        assertThat(cache.get("https://client.example.com/a")).isNotNull();

        Thread.sleep(600);

        assertThat(cache.get("https://client.example.com/a")).isNull();
    }

    @Test
    void shouldOverwriteSameKeyWithLatestDocument() {
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofHours(24), Duration.ofHours(1), 1000);

        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), null);
        assertThat(cache.get("https://client.example.com/a").clientName()).isEqualTo("Test Client");

        // 同 key 再次写入：覆盖为最新文档，条目数不变
        McpCimdMetadata updated = new McpCimdMetadata("https://client.example.com/a", "Updated Client",
                List.of("https://client.example.com/callback"),
                List.of("authorization_code"), List.of("code"), "none",
                null, null, null, null, null, "web");
        cache.put("https://client.example.com/a", updated, null);

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get("https://client.example.com/a").clientName()).isEqualTo("Updated Client");
    }

    @Test
    void shouldNotCacheBeyondMaxEntries() {
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofHours(24), Duration.ofHours(1), 2);

        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), null);
        cache.put("https://client.example.com/b", metadata("https://client.example.com/b"), null);
        // 第三条超限：不入缓存，不报错，旧条目不受影响
        cache.put("https://client.example.com/c", metadata("https://client.example.com/c"), null);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("https://client.example.com/c")).isNull();
        assertThat(cache.get("https://client.example.com/a")).isNotNull();
        assertThat(cache.get("https://client.example.com/b")).isNotNull();
    }

    @Test
    void shouldEvictExpiredEntriesBeforeRejectingNewEntry() throws InterruptedException {
        McpCimdMetadataCache cache = new McpCimdMetadataCache(
                Duration.ofHours(24), Duration.ofMillis(50), 1);

        cache.put("https://client.example.com/a", metadata("https://client.example.com/a"), null);
        Thread.sleep(150);
        // a 已过期：先清过期条目再入新条目，b 应能入缓存
        cache.put("https://client.example.com/b", metadata("https://client.example.com/b"), 3600L);

        assertThat(cache.get("https://client.example.com/a")).isNull();
        assertThat(cache.get("https://client.example.com/b")).isNotNull();
    }

}
