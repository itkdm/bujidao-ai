package cn.iocoder.yudao.module.mcp.framework.oauth2.config;

import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdFetcher;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdMetadataCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
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
            .withConfiguration(AutoConfigurations.of(McpOAuthConfiguration.class))
            .withBean(McpCimdClientService.class);

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
    void shouldFailBindingWhenFetchTimeoutIsZero() {
        contextRunner
                .withPropertyValues("yudao.mcp.oauth.cimd.fetch-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getFailure()).isNotNull();
                    // 失败信息（沿 cause 链）应提到 fetchTimeout：@AssertTrue 的校验信息是
                    // "cimd.fetchTimeout 必须大于 0"
                    assertThat(context.getFailure()).hasStackTraceContaining("fetchTimeout");
                });
    }

}
