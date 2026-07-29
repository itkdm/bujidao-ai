package cn.iocoder.yudao.module.mcp.dal.redis.oauth2;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.mcp.enums.McpRedisKeyConstants.MCP_OAUTH2_AUTHORIZATION_REQUEST_USED;

/**
 * MCP OAuth 授权请求 RedisDAO。
 *
 * @author bujidao
 */
@Repository
public class McpOAuthAuthorizationRequestRedisDAO {

    /**
     * 授权码默认 5 分钟过期，请求防重放标记保持同样窗口。
     */
    private static final long TIMEOUT_SECONDS = 5 * 60L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean markUsed(String requestId) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(formatKey(requestId), "1", TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static String formatKey(String requestId) {
        return String.format(MCP_OAUTH2_AUTHORIZATION_REQUEST_USED, requestId);
    }

}
