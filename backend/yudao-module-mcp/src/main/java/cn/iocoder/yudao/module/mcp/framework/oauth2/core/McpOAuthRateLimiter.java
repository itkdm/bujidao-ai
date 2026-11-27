package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP OAuth 限流器：进程内 60 秒滑动窗口计数 + CIMD 拉取并发信号量。
 *
 * <p>选型说明（先调查了项目里已有的限流实现再定）：
 * <ul>
 *     <li>上游 {@code @RateLimiter}（Redisson 全局限流）不适用：阈值写死在注解上、不能随
 *     {@link McpOAuthProperties} 配置调整；超限抛 {@code ServiceException(TOO_MANY_REQUESTS)}，
 *     走全局异常处理变成 CommonResult，而不是 OAuth 标准错误；且纯 Java 的
 *     {@code McpCimdFetcher} 没有 AOP 代理、拿不到请求上下文，注解挂不上去。</li>
 *     <li>Redis 限流（模块里已有 {@code StringRedisTemplate} 的 DAO）复用成本高：
 *     {@code McpCimdFetcher} / {@code McpCimdClientService} 在单测与单参构造里都是直接
 *     {@code new} 出来的，把 Redis 客户端穿进去要改构造签名、而测试文件不允许改；
 *     且拉取层本身不依赖 Spring。</li>
 *     <li>因此复用项目自研 ACF 的 {@code CapabilityRateLimitGuard} 同款模式：
 *     {@link ConcurrentHashMap} 分 key 滑动窗口 + {@link Clock} 可注入 + 定期清理过期窗口，
 *     阈值全部来自 {@link McpOAuthProperties.RateLimit}。</li>
 * </ul>
 *
 * <p><b>部署边界（不要误读）：本限流器只约束单个应用实例。多实例部署下每个实例各自计数，
 * 不是全局配额；如需跨实例全局限流，应在网关或 Redis 层再做一层，不要谎称这里是全局的。</b>
 * 过期窗口会定期清理，key 空间（host / IP）不会无限增长。
 *
 * @author bujidao
 */
public class McpOAuthRateLimiter {

    /** 滑动窗口长度：60 秒，所有“每分钟”阈值都按这个窗口计。 */
    private static final long WINDOW_MS = 60_000L;

    private static final int DEFAULT_CLEANUP_INTERVAL = 256;

    private final McpOAuthProperties.RateLimit config;
    private final Clock clock;
    private final int cleanupInterval;
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();
    private final AtomicLong operationCount = new AtomicLong();
    /** CIMD 拉取并发槽：只在限流开启时占用，见 {@link #tryAcquireCimdSlot()}。 */
    private final Semaphore cimdFetchSlots;

    public McpOAuthRateLimiter(McpOAuthProperties.RateLimit config) {
        this(config, Clock.systemUTC());
    }

    McpOAuthRateLimiter(McpOAuthProperties.RateLimit config, Clock clock) {
        this.config = config != null ? config : new McpOAuthProperties.RateLimit();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cleanupInterval = DEFAULT_CLEANUP_INTERVAL;
        this.cimdFetchSlots = new Semaphore(Math.max(1, this.config.getCimdMaxConcurrentFetches()));
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * CIMD 拉取速率：先过全局窗口，再过单 host 窗口；任一超限返回 false。
     * 注意：全局窗口通过、单 host 窗口拒绝时，全局窗口已消耗的一次计数不会回补——
     * 全局上限只是防拖垮的安全阀，单次多计一次无实际影响。
     */
    public boolean tryAcquireCimdFetch(String host) {
        if (!config.isEnabled()) {
            return true;
        }
        String normalizedHost = StrUtil.isBlank(host) ? "unknown" : host.toLowerCase(Locale.ROOT);
        if (!tryAcquire("cimd:global", config.getCimdGlobalPerMinute())) {
            return false;
        }
        return tryAcquire("cimd:host:" + normalizedHost, config.getCimdPerHostPerMinute());
    }

    /**
     * CIMD 拉取并发槽：拿到返回 true 才允许发请求，请求结束（成功或失败）必须调
     * {@link #releaseCimdSlot()} 释放；没拿到返回 false，调用方直接按限流拒绝，
     * 不要发请求。限流关闭时直接返回 true、不占槽。
     */
    public boolean tryAcquireCimdSlot() {
        if (!config.isEnabled()) {
            return true;
        }
        return cimdFetchSlots.tryAcquire();
    }

    /** 释放 {@link #tryAcquireCimdSlot()} 占到的并发槽；没占到槽时不要调用。 */
    public void releaseCimdSlot() {
        if (!config.isEnabled()) {
            return;
        }
        cimdFetchSlots.release();
    }

    /** 授权同意（POST /mcp/oauth2/authorize）：按调用方 IP 限流。 */
    public boolean tryAcquireAuthorize(String clientIp) {
        if (!config.isEnabled()) {
            return true;
        }
        return tryAcquire("authorize:ip:" + ipKey(clientIp), config.getAuthorizePerIpPerMinute());
    }

    /** token 兑换（POST /mcp/oauth2/token）：按调用方 IP 限流。 */
    public boolean tryAcquireToken(String clientIp) {
        if (!config.isEnabled()) {
            return true;
        }
        return tryAcquire("token:ip:" + ipKey(clientIp), config.getTokenPerIpPerMinute());
    }

    /** 动态客户端注册（POST /mcp/oauth2/register）：按调用方 IP 限流。 */
    public boolean tryAcquireRegister(String clientIp) {
        if (!config.isEnabled()) {
            return true;
        }
        return tryAcquire("register:ip:" + ipKey(clientIp), config.getRegisterPerIpPerMinute());
    }

    private static String ipKey(String clientIp) {
        // IPv6 同一地址大小写不同是同一地址，归一小写避免分到两个窗口、限额翻倍。
        return StrUtil.isBlank(clientIp) ? "unknown" : clientIp.trim().toLowerCase(Locale.ROOT);
    }

    private boolean tryAcquire(String key, int limit) {
        long now = clock.millis();
        boolean[] allowed = new boolean[1];
        windows.compute(key, (ignored, current) -> {
            WindowState state = current == null ? new WindowState(now) : current;
            allowed[0] = state.tryAcquire(limit, now);
            return state;
        });
        cleanupStaleWindows(now);
        return allowed[0];
    }

    private void cleanupStaleWindows(long now) {
        if (operationCount.incrementAndGet() % cleanupInterval != 0) {
            return;
        }
        // 使用 computeIfPresent 与同 key 的请求串行，避免清理线程删除刚刚恢复活跃的窗口。
        for (String key : windows.keySet()) {
            windows.computeIfPresent(key, (ignored, state) -> state.isExpired(now) ? null : state);
        }
    }

    /** 单个 key 的滑动窗口：窗口内已接受的时间戳队列。 */
    private static final class WindowState {

        private final Deque<Long> acceptedAt = new ArrayDeque<>();
        private long lastAccessAt;

        private WindowState(long now) {
            this.lastAccessAt = now;
        }

        private boolean tryAcquire(int limit, long now) {
            lastAccessAt = Math.max(now, lastAccessAt);
            long windowStart = now - WINDOW_MS;
            while (!acceptedAt.isEmpty() && acceptedAt.peekFirst() <= windowStart) {
                acceptedAt.removeFirst();
            }
            if (acceptedAt.size() >= limit) {
                return false;
            }
            acceptedAt.addLast(now);
            return true;
        }

        private boolean isExpired(long now) {
            return acceptedAt.isEmpty() || acceptedAt.peekLast() + WINDOW_MS <= now;
        }

    }

}
