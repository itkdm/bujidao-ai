package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CIMD 文档进程内内存缓存（ConcurrentHashMap，不引入 Caffeine，项目里没有它）。
 *
 * <p>规则：
 * <ul>
 *   <li>key 是完整 client_id URL；失败或无效文档永不入缓存（只有 {@link #put} 能写，
 *       而调用方只在拉取 + 校验成功后才调它）；</li>
 *   <li>过期时间优先尊重响应 Cache-Control 的 max-age，但硬上限为 cacheMaxAge（默认 24 小时）；
 *       无缓存头时用 cacheDefaultTtl（默认 1 小时）；max-age=0 的文档不入缓存；</li>
 *   <li>读到过期条目视为未命中并删除；</li>
 *   <li>条目数上限默认 1000：超限时先清过期条目，仍超限则新文档不入缓存（每次现拉，不报错）。</li>
 * </ul>
 *
 * @author bujidao
 */
public class McpCimdMetadataCache {

    private final Duration cacheMaxAge;
    private final Duration cacheDefaultTtl;
    private final int maxEntries;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public McpCimdMetadataCache(Duration cacheMaxAge, Duration cacheDefaultTtl, int maxEntries) {
        this.cacheMaxAge = cacheMaxAge;
        this.cacheDefaultTtl = cacheDefaultTtl;
        this.maxEntries = maxEntries;
    }

    /**
     * 查缓存：命中且未过期返回文档；未命中或已过期返回 null（过期条目会被删除）。
     */
    public McpCimdMetadata get(String clientIdUrl) {
        Entry entry = entries.get(clientIdUrl);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(clientIdUrl, entry);
            return null;
        }
        return entry.metadata();
    }

    /**
     * 写缓存。只应在拉取 + 校验成功后调用，失败 / 无效文档永不入缓存。
     *
     * @param responseMaxAgeSeconds 响应 Cache-Control 的 max-age（秒），无缓存头时传 null
     */
    public void put(String clientIdUrl, McpCimdMetadata metadata, Long responseMaxAgeSeconds) {
        Duration ttl = responseMaxAgeSeconds == null
                ? cacheDefaultTtl : Duration.ofSeconds(responseMaxAgeSeconds);
        if (ttl.isZero() || ttl.isNegative()) {
            // max-age=0 表示文档立即过期，不入缓存
            return;
        }
        if (ttl.compareTo(cacheMaxAge) > 0) {
            // 硬上限：响应要求缓存再久也不超过 cacheMaxAge
            ttl = cacheMaxAge;
        }
        if (entries.size() >= maxEntries && !entries.containsKey(clientIdUrl)) {
            evictExpired();
            if (entries.size() >= maxEntries) {
                // 仍超限：新文档不入缓存，本次现拉返回，不报错
                return;
            }
        }
        Instant now = Instant.now();
        entries.put(clientIdUrl, new Entry(metadata, now, now.plus(ttl)));
    }

    /** 清除所有过期条目。 */
    public void evictExpired() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    /** 当前条目数（含未过期检查前的过期条目），供测试与运维观察。 */
    public int size() {
        return entries.size();
    }

    /** 清空缓存，供测试使用。 */
    public void clear() {
        entries.clear();
    }

    private record Entry(McpCimdMetadata metadata, Instant fetchedAt, Instant expiresAt) {
    }

}
