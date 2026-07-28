package cn.iocoder.yudao.module.system.dal.redis.oauth2;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants.OAUTH2_AUTHORIZATION_CODE_EXTRA;

/**
 * OAuth2 授权码扩展信息的 RedisDAO。
 *
 * @author bujidao
 */
@Repository
public class OAuth2AuthorizationCodeExtraRedisDAO {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public OAuth2AuthorizationCodeExtraDO get(String code) {
        return JsonUtils.parseObject(stringRedisTemplate.opsForValue().get(formatKey(code)),
                OAuth2AuthorizationCodeExtraDO.class);
    }

    public void set(OAuth2AuthorizationCodeExtraDO codeExtra) {
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
        return String.format(OAUTH2_AUTHORIZATION_CODE_EXTRA, code);
    }

}
