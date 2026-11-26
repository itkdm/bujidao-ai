package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link McpOAuthRateLimiter} 的单元测试。
 *
 * <p>本类与被测类同包，才能用包级构造器注入可拨动的 {@link Clock}，
 * 确定性地验证 60 秒滑动窗口过期后恢复，不用真的等 60 秒。
 */
class McpOAuthRateLimiterTest {

    private static final Instant START = Instant.parse("2026-11-27T00:00:00Z");

    @Test
    void shouldAllowAllWhenDisabled() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setEnabled(false);
        config.setTokenPerIpPerMinute(1);
        config.setAuthorizePerIpPerMinute(1);
        config.setCimdPerHostPerMinute(1);
        config.setCimdGlobalPerMinute(1);
        config.setCimdMaxConcurrentFetches(1);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config, new MutableClock(START));

        assertThat(limiter.isEnabled()).isFalse();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquireToken("1.2.3.4")).isTrue();
            assertThat(limiter.tryAcquireAuthorize("1.2.3.4")).isTrue();
            assertThat(limiter.tryAcquireCimdFetch("client.example.com")).isTrue();
            // 关闭时不占并发槽：连续拿也不拒绝，释放也是空操作
            assertThat(limiter.tryAcquireCimdSlot()).isTrue();
        }
        limiter.releaseCimdSlot();
    }

    @Test
    void shouldRejectTokenRequestsOverPerIpLimitAndRecoverAfterWindow() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setTokenPerIpPerMinute(2);
        MutableClock clock = new MutableClock(START);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config, clock);

        assertThat(limiter.tryAcquireToken("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquireToken("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquireToken("1.2.3.4")).isFalse();
        // 其它 IP 不受影响
        assertThat(limiter.tryAcquireToken("5.6.7.8")).isTrue();
        // 59 秒后旧记录仍在窗口内，继续拒绝
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquireToken("1.2.3.4")).isFalse();
        // 滑过 60 秒窗口：旧记录过期，恢复放行
        clock.advance(Duration.ofSeconds(2));
        assertThat(limiter.tryAcquireToken("1.2.3.4")).isTrue();
    }

    @Test
    void shouldRejectAuthorizeRequestsOverPerIpLimitAndRecoverAfterWindow() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setAuthorizePerIpPerMinute(1);
        MutableClock clock = new MutableClock(START);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config, clock);

        assertThat(limiter.tryAcquireAuthorize("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquireAuthorize("1.2.3.4")).isFalse();

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquireAuthorize("1.2.3.4")).isTrue();
    }

    @Test
    void shouldEnforceCimdPerHostAndGlobalLimits() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setCimdPerHostPerMinute(2);
        config.setCimdGlobalPerMinute(3);
        MutableClock clock = new MutableClock(START);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config, clock);

        assertThat(limiter.tryAcquireCimdFetch("a.example.com")).isTrue();
        assertThat(limiter.tryAcquireCimdFetch("a.example.com")).isTrue();
        // 单 host 到上限：全局窗口已消耗一次计数（第 3 次全局放行、单 host 拒绝），
        // 所以接下来其它 host 也被全局上限挡住
        assertThat(limiter.tryAcquireCimdFetch("a.example.com")).isFalse();
        assertThat(limiter.tryAcquireCimdFetch("b.example.com")).isFalse();

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquireCimdFetch("a.example.com")).isTrue();
        assertThat(limiter.tryAcquireCimdFetch("b.example.com")).isTrue();
    }

    @Test
    void shouldEnforceCimdConcurrentSlots() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setCimdMaxConcurrentFetches(1);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config, new MutableClock(START));

        assertThat(limiter.tryAcquireCimdSlot()).isTrue();
        assertThat(limiter.tryAcquireCimdSlot()).isFalse();
        limiter.releaseCimdSlot();
        assertThat(limiter.tryAcquireCimdSlot()).isTrue();
        limiter.releaseCimdSlot();
    }

    @Test
    void shouldTreatBlankHostAndIpAsUnknown() {
        McpOAuthProperties.RateLimit config = new McpOAuthProperties.RateLimit();
        config.setCimdPerHostPerMinute(1);
        config.setTokenPerIpPerMinute(1);
        McpOAuthRateLimiter limiter = new McpOAuthRateLimiter(config, new MutableClock(START));

        // null / 空白 host 共用 "unknown" key：第一次放行，第二次拒绝
        assertThat(limiter.tryAcquireCimdFetch(null)).isTrue();
        assertThat(limiter.tryAcquireCimdFetch("")).isFalse();
        assertThat(limiter.tryAcquireCimdFetch("  ")).isFalse();
        // null / 空白 IP 同理
        assertThat(limiter.tryAcquireToken(null)).isTrue();
        assertThat(limiter.tryAcquireToken("")).isFalse();
        // host 大小写归一化：同一 host 不同大小写共用一个窗口
        assertThat(limiter.tryAcquireCimdFetch("Client.Example.COM")).isTrue();
        assertThat(limiter.tryAcquireCimdFetch("client.example.com")).isFalse();
    }

    /** 可拨动的 Clock：只改 instant，不依赖真实时间流逝。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

    }

}
