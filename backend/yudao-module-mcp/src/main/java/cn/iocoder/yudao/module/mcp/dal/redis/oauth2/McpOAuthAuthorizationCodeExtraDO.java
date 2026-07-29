package cn.iocoder.yudao.module.mcp.dal.redis.oauth2;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP OAuth 授权码扩展信息。
 *
 * @author bujidao
 */
@Data
public class McpOAuthAuthorizationCodeExtraDO {

    /**
     * 授权码。
     */
    private String code;
    /**
     * 客户端编号。
     */
    private String clientId;
    /**
     * 重定向地址。
     */
    private String redirectUri;
    /**
     * PKCE code challenge。
     */
    private String codeChallenge;
    /**
     * PKCE code challenge method。
     */
    private String codeChallengeMethod;
    /**
     * OAuth resource indicator。
     */
    private String resource;
    /**
     * 用户编号。
     */
    private Long userId;
    /**
     * 用户类型。
     */
    private Integer userType;
    /**
     * 租户编号。
     */
    private Long tenantId;
    /**
     * 过期时间。
     */
    private LocalDateTime expiresTime;

}
