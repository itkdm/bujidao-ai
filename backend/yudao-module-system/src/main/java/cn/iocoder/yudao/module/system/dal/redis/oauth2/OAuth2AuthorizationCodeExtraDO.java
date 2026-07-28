package cn.iocoder.yudao.module.system.dal.redis.oauth2;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2 授权码的扩展信息。
 *
 * @author bujidao
 */
@Data
public class OAuth2AuthorizationCodeExtraDO {

    /**
     * 授权码
     */
    private String code;
    /**
     * 客户端编号
     */
    private String clientId;
    /**
     * 重定向地址
     */
    private String redirectUri;
    /**
     * PKCE code challenge
     */
    private String codeChallenge;
    /**
     * PKCE code challenge method
     */
    private String codeChallengeMethod;
    /**
     * OAuth resource indicator
     */
    private String resource;
    /**
     * 过期时间
     */
    private LocalDateTime expiresTime;

}
