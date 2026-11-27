package cn.iocoder.yudao.module.mcp.framework.oauth2.config;

import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdFetcher;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdMetadataCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpOAuthProperties（前缀 yudao.mcp.oauth）绑定测试。
 *
 * <p>背景：application.yaml 里 OAuth 相关键曾挂在 yudao.mcp.acf.oauth.* 下，
 * 那个前缀没有任何 @ConfigurationProperties 绑定，写了也不生效；
 * 真正生效的是本类绑定的 yudao.mcp.oauth.*（字段默认值恰好与 yaml 里的值相同，
 * 所以此前行为上没暴露问题）。本测试锁定正确前缀的绑定行为。
 */
class McpOAuthPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // ValidationAutoConfiguration 提供 Validator bean：fetch-timeout=0s 的失败断言
            // 依赖 @AssertTrue 校验，没有它测试不会失败而变红（与 YudaoAcfAutoConfigurationTest 同一惯例）
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class,
                    McpOAuthConfiguration.class))
            .withBean(McpCimdClientService.class);

    /**
     * 不带 McpCimdClientService 的 Runner：rate-limit 的 enabled=false 会让
     * McpOAuthConfiguration 里的 Fetcher / Cache Bean 都不建，ClientService
     * （构造器要 Fetcher + Cache）也就建不出来；只验 Properties 绑定时不用它。
     */
    private final ApplicationContextRunner propertiesOnlyContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class,
                    McpOAuthConfiguration.class));

    @Test
    void shouldBindCimdProperties() {
        contextRunner
                .withPropertyValues(
                        "yudao.mcp.oauth.cimd.fetch-timeout=5s",
                        "yudao.mcp.oauth.cimd.max-document-bytes=8192",
                        "yudao.mcp.oauth.cimd.cache-max-age=2h",
                        "yudao.mcp.oauth.cimd.cache-default-ttl=30m",
                        "yudao.mcp.oauth.cimd.cache-max-entries=42",
                        "yudao.mcp.oauth.cimd.allow-private-addresses=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpOAuthProperties.class);
                    McpOAuthProperties properties = context.getBean(McpOAuthProperties.class);
                    assertThat(properties.getCimd().getFetchTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.getCimd().getMaxDocumentBytes()).isEqualTo(8192);
                    assertThat(properties.getCimd().getCacheMaxAge()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.getCimd().getCacheDefaultTtl()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.getCimd().getCacheMaxEntries()).isEqualTo(42);
                    assertThat(properties.getCimd().isAllowPrivateAddresses()).isTrue();
                    // Fetcher / Cache / Service 三个 Bean 都应随配置建出来
                    assertThat(context).hasSingleBean(McpCimdFetcher.class);
                    assertThat(context).hasSingleBean(McpCimdMetadataCache.class);
                    assertThat(context).hasSingleBean(McpCimdClientService.class);
                });
    }

    @Test
    void shouldBindRateLimitProperties() {
        // enabled=false 时 Fetcher / Cache Bean 都不建（见 propertiesOnlyContextRunner 的说明），
        // 这里只断言 Properties 绑定本身，不验那几个 Bean。
        propertiesOnlyContextRunner
                .withPropertyValues(
                        "yudao.mcp.oauth.rate-limit.enabled=false",
                        "yudao.mcp.oauth.rate-limit.cimd-per-host-per-minute=10",
                        "yudao.mcp.oauth.rate-limit.cimd-global-per-minute=100",
                        "yudao.mcp.oauth.rate-limit.cimd-max-concurrent-fetches=5",
                        "yudao.mcp.oauth.rate-limit.authorize-per-ip-per-minute=30",
                        "yudao.mcp.oauth.rate-limit.token-per-ip-per-minute=45")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpOAuthProperties.class);
                    McpOAuthProperties.RateLimit rateLimit =
                            context.getBean(McpOAuthProperties.class).getRateLimit();
                    assertThat(rateLimit.isEnabled()).isFalse();
                    assertThat(rateLimit.getCimdPerHostPerMinute()).isEqualTo(10);
                    assertThat(rateLimit.getCimdGlobalPerMinute()).isEqualTo(100);
                    assertThat(rateLimit.getCimdMaxConcurrentFetches()).isEqualTo(5);
                    assertThat(rateLimit.getAuthorizePerIpPerMinute()).isEqualTo(30);
                    assertThat(rateLimit.getTokenPerIpPerMinute()).isEqualTo(45);
                });
    }

    @Test
    void shouldUseRateLimitDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(McpOAuthProperties.class);
            McpOAuthProperties.RateLimit rateLimit =
                    context.getBean(McpOAuthProperties.class).getRateLimit();
            assertThat(rateLimit.isEnabled()).isTrue();
            assertThat(rateLimit.getCimdPerHostPerMinute()).isEqualTo(60);
            assertThat(rateLimit.getCimdGlobalPerMinute()).isEqualTo(600);
            assertThat(rateLimit.getCimdMaxConcurrentFetches()).isEqualTo(20);
            assertThat(rateLimit.getAuthorizePerIpPerMinute()).isEqualTo(60);
            assertThat(rateLimit.getTokenPerIpPerMinute()).isEqualTo(60);
        });
    }

    @Test
    void shouldFailBindingWhenFetchTimeoutIsZero() {
        contextRunner
                .withPropertyValues("yudao.mcp.oauth.cimd.fetch-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                    // 失败信息（沿 cause 链）应提到 fetchTimeout：@AssertTrue 的校验信息是
                    // "cimd.fetchTimeout 必须大于 0"
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("fetchTimeout");
                });
    }

}
