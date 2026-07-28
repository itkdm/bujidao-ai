package cn.iocoder.yudao.module.system.service.oauth2;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * OAuth2 授予 Service 实现类
 *
 * @author 芋道源码
 */
@Service
public class OAuth2GrantServiceImpl implements OAuth2GrantService {

    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private OAuth2CodeService oauth2CodeService;
    @Resource
    private AdminAuthService adminAuthService;
    @Resource
    private OAuth2AuthorizationCodeExtraRedisDAO oauth2AuthorizationCodeExtraRedisDAO;

    @Override
    public OAuth2AccessTokenDO grantImplicit(Long userId, Integer userType,
                                             String clientId, List<String> scopes) {
        return oauth2TokenService.createAccessToken(userId, userType, clientId, scopes);
    }

    @Override
    public String grantAuthorizationCodeForCode(Long userId, Integer userType,
                                                String clientId, List<String> scopes,
                                                String redirectUri, String state) {
        return grantAuthorizationCodeForCode(userId, userType, clientId, scopes, redirectUri, state,
                null, null, null);
    }

    @Override
    public String grantAuthorizationCodeForCode(Long userId, Integer userType,
                                                String clientId, List<String> scopes,
                                                String redirectUri, String state,
                                                String codeChallenge, String codeChallengeMethod, String resource) {
        validateCodeChallengeMethod(codeChallenge, codeChallengeMethod);
        OAuth2CodeDO codeDO = oauth2CodeService.createAuthorizationCode(userId, userType, clientId, scopes,
                redirectUri, state);
        saveAuthorizationCodeExtra(codeDO, codeChallenge, codeChallengeMethod, resource);
        return codeDO.getCode();
    }

    @Override
    public OAuth2AccessTokenDO grantAuthorizationCodeForAccessToken(String clientId, String code,
                                                                    String redirectUri, String state) {
        return grantAuthorizationCodeForAccessToken(clientId, code, redirectUri, state, null, null, false);
    }

    @Override
    public OAuth2AccessTokenDO grantAuthorizationCodeForAccessToken(String clientId, String code,
                                                                    String redirectUri, String state,
                                                                    String codeVerifier, String resource,
                                                                    boolean pkceRequired) {
        OAuth2AuthorizationCodeExtraDO codeExtra = oauth2AuthorizationCodeExtraRedisDAO.get(code);
        OAuth2CodeDO codeDO = oauth2CodeService.consumeAuthorizationCode(code);
        Assert.notNull(codeDO, "授权码不能为空"); // 防御性编程
        // 校验 clientId 是否匹配
        if (!StrUtil.equals(clientId, codeDO.getClientId())) {
            throw exception(ErrorCodeConstants.OAUTH2_GRANT_CLIENT_ID_MISMATCH);
        }
        // 校验 redirectUri 是否匹配
        if (!StrUtil.equals(redirectUri, codeDO.getRedirectUri())) {
            throw exception(ErrorCodeConstants.OAUTH2_GRANT_REDIRECT_URI_MISMATCH);
        }
        // 校验 state 是否匹配
        state = StrUtil.nullToDefault(state, ""); // 数据库 state 为 null 时，会设置为 "" 空串
        if (!StrUtil.equals(state, codeDO.getState())) {
            throw exception(ErrorCodeConstants.OAUTH2_GRANT_STATE_MISMATCH);
        }
        validateAuthorizationCodeExtra(codeExtra, codeVerifier, resource, pkceRequired);
        oauth2AuthorizationCodeExtraRedisDAO.delete(code);

        // 创建访问令牌
        return oauth2TokenService.createAccessToken(codeDO.getUserId(), codeDO.getUserType(),
                codeDO.getClientId(), codeDO.getScopes());
    }

    @Override
    public OAuth2AccessTokenDO grantPassword(String username, String password, String clientId, List<String> scopes) {
        // 使用账号 + 密码进行登录
        AdminUserDO user = adminAuthService.authenticate(username, password);
        Assert.notNull(user, "用户不能为空！"); // 防御性编程

        // 创建访问令牌
        return oauth2TokenService.createAccessToken(user.getId(), UserTypeEnum.ADMIN.getValue(), clientId, scopes);
    }

    @Override
    public OAuth2AccessTokenDO grantRefreshToken(String refreshToken, String clientId) {
        return oauth2TokenService.refreshAccessToken(refreshToken, clientId);
    }

    @Override
    public OAuth2AccessTokenDO grantClientCredentials(String clientId, List<String> scopes) {
        // 特殊：https://yuanbao.tencent.com/bot/app/share/chat/wFj642xSZHHx
        return oauth2TokenService.createAccessToken(0L, UserTypeEnum.ADMIN.getValue(), clientId, scopes);
    }

    @Override
    public boolean revokeToken(String clientId, String accessToken) {
        // 先查询，保证 clientId 时匹配的
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.getAccessToken(accessToken);
        if (accessTokenDO == null || ObjectUtil.notEqual(clientId, accessTokenDO.getClientId())) {
            return false;
        }
        // 再删除
        return oauth2TokenService.removeAccessToken(accessToken) != null;
    }

    private void saveAuthorizationCodeExtra(OAuth2CodeDO codeDO, String codeChallenge,
                                            String codeChallengeMethod, String resource) {
        if (StrUtil.isAllBlank(codeChallenge, resource)) {
            return;
        }
        OAuth2AuthorizationCodeExtraDO codeExtra = new OAuth2AuthorizationCodeExtraDO()
                .setCode(codeDO.getCode())
                .setClientId(codeDO.getClientId())
                .setRedirectUri(codeDO.getRedirectUri())
                .setCodeChallenge(codeChallenge)
                .setCodeChallengeMethod(codeChallengeMethod)
                .setResource(resource)
                .setExpiresTime(codeDO.getExpiresTime());
        oauth2AuthorizationCodeExtraRedisDAO.set(codeExtra);
    }

    private static void validateCodeChallengeMethod(String codeChallenge, String codeChallengeMethod) {
        if (StrUtil.isNotBlank(codeChallenge) && !StrUtil.equals(codeChallengeMethod, "S256")) {
            throw exception(ErrorCodeConstants.OAUTH2_GRANT_CODE_CHALLENGE_METHOD_UNSUPPORTED);
        }
    }

    private static void validateAuthorizationCodeExtra(OAuth2AuthorizationCodeExtraDO codeExtra, String codeVerifier,
                                                       String resource, boolean pkceRequired) {
        if (codeExtra == null) {
            if (pkceRequired || StrUtil.isNotBlank(codeVerifier) || StrUtil.isNotBlank(resource)) {
                throw exception(ErrorCodeConstants.OAUTH2_GRANT_CODE_VERIFIER_MISMATCH);
            }
            return;
        }
        if (StrUtil.isNotBlank(codeExtra.getCodeChallenge()) || pkceRequired) {
            if (StrUtil.isBlank(codeVerifier)) {
                throw exception(ErrorCodeConstants.OAUTH2_GRANT_CODE_VERIFIER_MISSING);
            }
            if (!StrUtil.equals(codeExtra.getCodeChallenge(), buildS256CodeChallenge(codeVerifier))) {
                throw exception(ErrorCodeConstants.OAUTH2_GRANT_CODE_VERIFIER_MISMATCH);
            }
        }
        if (!StrUtil.equals(StrUtil.nullToEmpty(resource), StrUtil.nullToEmpty(codeExtra.getResource()))) {
            throw exception(ErrorCodeConstants.OAUTH2_GRANT_RESOURCE_MISMATCH);
        }
    }

    private static String buildS256CodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

}
