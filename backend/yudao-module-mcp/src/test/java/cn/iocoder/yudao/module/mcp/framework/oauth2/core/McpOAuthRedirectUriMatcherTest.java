package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link McpOAuthRedirectUriMatcher} 的单元测试。
 */
class McpOAuthRedirectUriMatcherTest {

    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1");

    @Test
    void shouldMatchExactUri() {
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:10244/callback",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isTrue();
        assertThat(McpOAuthRedirectUriMatcher.matches("https://app.example.com/callback",
                List.of("https://app.example.com/callback"), LOCAL_HOSTS)).isTrue();
        // 自定义 scheme 精确相等也通过：放宽只针对回环换端口，不针对 scheme 本身
        assertThat(McpOAuthRedirectUriMatcher.matches("workbuddy://oauth/callback",
                List.of("workbuddy://oauth/callback"), LOCAL_HOSTS)).isTrue();
    }

    @Test
    void shouldMatchLoopbackWithDifferentPort() {
        // 注册时登记 10244，客户端重启后回调端口变成 54321：只忽略端口
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isTrue();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://localhost:54321/callback",
                List.of("http://localhost:10244/callback"), LOCAL_HOSTS)).isTrue();
        // 注册时没写端口、请求带端口，同样放行
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback",
                List.of("http://127.0.0.1/callback"), LOCAL_HOSTS)).isTrue();
        // path 带 query 时 query 也必须相同
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback?x=1",
                List.of("http://127.0.0.1:10244/callback?x=1"), LOCAL_HOSTS)).isTrue();
    }

    @Test
    void shouldRejectNonLoopbackWithDifferentPort() {
        assertThat(McpOAuthRedirectUriMatcher.matches("https://app.example.com:8443/callback",
                List.of("https://app.example.com/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("https://app.example.com/callback",
                List.of("https://app.example.com:8443/callback"), LOCAL_HOSTS)).isFalse();
    }

    @Test
    void shouldRejectLoopbackWithDifferentPathOrQuery() {
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/other",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback?x=2",
                List.of("http://127.0.0.1:10244/callback?x=1"), LOCAL_HOSTS)).isFalse();
        // scheme 不同也不放行
        assertThat(McpOAuthRedirectUriMatcher.matches("https://127.0.0.1:54321/callback",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
    }

    @Test
    void shouldTreatTrailingDotHostAsSame() {
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1.:54321/callback",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isTrue();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://localhost.:54321/callback",
                List.of("http://localhost:10244/callback"), LOCAL_HOSTS)).isTrue();
    }

    @Test
    void shouldRejectFragmentUserinfoAndInvalidUri() {
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback#frag",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://user@127.0.0.1:54321/callback",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("not a uri",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback",
                List.of("http://127.0.0.1:10244/callback#frag"), LOCAL_HOSTS)).isFalse();
    }

    @Test
    void shouldRejectBlankOrEmptyInput() {
        assertThat(McpOAuthRedirectUriMatcher.matches(null,
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("  ",
                List.of("http://127.0.0.1:10244/callback"), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback",
                List.of(), LOCAL_HOSTS)).isFalse();
        assertThat(McpOAuthRedirectUriMatcher.matches("http://127.0.0.1:54321/callback",
                null, LOCAL_HOSTS)).isFalse();
    }

}
