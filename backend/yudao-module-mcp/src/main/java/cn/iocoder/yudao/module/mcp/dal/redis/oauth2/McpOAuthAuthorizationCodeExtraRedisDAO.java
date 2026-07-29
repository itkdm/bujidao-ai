package cn.iocoder.yudao.module.mcp.dal.redis.oauth2;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.mcp.enums.McpRedisKeyConstants.MCP_OAUTH2_AUTHORIZATION_CODE_EXTRA;

/**
 * MCP OAuth 授权码扩展信息 RedisDAO。
 *
 * @author bujidao
 */
@Repository
public class McpOAuthAuthorizationCodeExtraRedisDAO {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public McpOAuthAuthorizationCodeExtraDO get(String code) {
        return JsonUtils.parseObject(stringRedisTemplate.opsForValue().get(formatKey(code)),
                McpOAuthAuthorizationCodeExtraDO.class);
    }

    public void set(McpOAuthAuthorizationCodeExtraDO codeExtra) {
        long timeout = LocalDateTimeUtil.between(LocalDateTime.now(), codeExtra.getExpiresTime(), ChronoUnit.SECONDS);
        if (timeout > 0) {
            stringRedisTemplate.opsForValue().set(formatKey(codeExtra.getCode()),
                    JsonUtils.toJsonString(codeExtra), timeout, TimeUnit.SECONDS);
        }
    }

    public void delete(String code) {
        stringRedisTemplate.delete(formatKey(code));
    }

    private static String formatKey(String code) {
        return String.format(MCP_OAUTH2_AUTHORIZATION_CODE_EXTRA, code);
    }

}
