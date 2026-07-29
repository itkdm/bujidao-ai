package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationRequestRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthPkceVerifier;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceValidator;
import cn.iocoder.yudao.module.system.convert.oauth2.OAuth2OpenConvert;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ApproveDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2GrantTypeEnum;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ApproveService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2CodeService;
import cn.iocoder.yudao.module.system.util.oauth2.OAuth2Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * MCP OAuth 授权码授权 Service。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
public class McpOAuthAuthorizationService {

    private final OAuth2ClientService oauth2ClientService;
    private final OAuth2ApproveService oauth2ApproveService;
    private final OAuth2CodeService oauth2CodeService;
    private final McpOAuthAuthorizationCodeExtraRedisDAO codeExtraRedisDAO;
    private final McpOAuthAuthorizationRequestRedisDAO authorizationRequestRedisDAO;

    public OAuth2OpenAuthorizeInfoRespVO getAuthorizeInfo(String clientId) {
        OAuth2ClientDO client = oauth2ClientService.validOAuthClientFromCache(clientId);
        List<OAuth2ApproveDO> approves = oauth2ApproveService.getApproveList(
                SecurityFrameworkUtils.getLoginUserId(), getUserType(), clientId);
        return OAuth2OpenConvert.INSTANCE.convert(client, approves);
    }

    public String approveOrDeny(String responseType, String clientId, String scope, String redirectUri,
                                Boolean autoApprove, String state, String codeChallenge,
                                String codeChallengeMethod, String resource, String expectedResource) {
        @SuppressWarnings("unchecked")
        Map<String, Boolean> scopes = JsonUtils.parseObject(scope, Map.class);
        scopes = ObjectUtil.defaultIfNull(scopes, Collections.emptyMap());

        OAuth2GrantTypeEnum grantTypeEnum = getGrantTypeEnum(responseType);
        OAuth2ClientDO client = oauth2ClientService.validOAuthClientFromCache(clientId, null,
                grantTypeEnum.getGrantType(), scopes.keySet(), null);
        validateRedirectUri(client, redirectUri);
        validatePkceAndResource(grantTypeEnum, client, codeChallenge, codeChallengeMethod, resource, expectedResource);

        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (Boolean.TRUE.equals(autoApprove)) {
            if (!oauth2ApproveService.checkForPreApproval(userId, getUserType(), clientId, scopes.keySet())) {
                return null;
            }
            markAuthorizationRequestUsed(userId, clientId, scopes, redirectUri, state,
                    codeChallenge, codeChallengeMethod, resource);
        } else if (!oauth2ApproveService.updateAfterApproval(userId, getUserType(), clientId, scopes)) {
            markAuthorizationRequestUsed(userId, clientId, scopes, redirectUri, state,
                    codeChallenge, codeChallengeMethod, resource);
            return OAuth2Utils.buildUnsuccessfulRedirect(redirectUri, responseType, state,
                    "access_denied", "User denied access");
        } else {
            markAuthorizationRequestUsed(userId, clientId, scopes, redirectUri, state,
                    codeChallenge, codeChallengeMethod, resource);
        }

        List<String> approveScopes = convertList(scopes.entrySet(), Map.Entry::getKey, Map.Entry::getValue);
        String authorizationCode = createAuthorizationCode(userId, clientId, approveScopes,
                redirectUri, state, codeChallenge, codeChallengeMethod, resource);
        return OAuth2Utils.buildAuthorizationCodeRedirectUri(redirectUri, authorizationCode, state);
    }

    private String createAuthorizationCode(Long userId, String clientId, List<String> scopes,
                                           String redirectUri, String state, String codeChallenge,
                                           String codeChallengeMethod, String resource) {
        var codeDO = oauth2CodeService.createAuthorizationCode(userId, getUserType(), clientId, scopes,
                redirectUri, state);
        codeExtraRedisDAO.set(new McpOAuthAuthorizationCodeExtraDO()
                .setCode(codeDO.getCode())
                .setClientId(codeDO.getClientId())
                .setRedirectUri(codeDO.getRedirectUri())
                .setCodeChallenge(codeChallenge)
                .setCodeChallengeMethod(codeChallengeMethod)
                .setResource(resource)
                .setUserId(codeDO.getUserId())
                .setUserType(codeDO.getUserType())
                .setTenantId(TenantContextHolder.getTenantId())
                .setExpiresTime(codeDO.getExpiresTime()));
        return codeDO.getCode();
    }

    private void markAuthorizationRequestUsed(Long userId, String clientId, Map<String, Boolean> scopes,
                                              String redirectUri, String state, String codeChallenge,
                                              String codeChallengeMethod, String resource) {
        String requestId = buildAuthorizationRequestId(userId, clientId, scopes, redirectUri, state,
                codeChallenge, codeChallengeMethod, resource);
        if (!authorizationRequestRedisDAO.markUsed(requestId)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "授权请求已处理，请重新发起认证");
        }
    }

    private static String buildAuthorizationRequestId(Long userId, String clientId, Map<String, Boolean> scopes,
                                                      String redirectUri, String state, String codeChallenge,
                                                      String codeChallengeMethod, String resource) {
        String scopeKeys = String.join(" ", scopes.keySet().stream().sorted().toList());
        String plain = String.join("\n",
                String.valueOf(userId),
                String.valueOf(getUserType()),
                StrUtil.nullToEmpty(clientId),
                StrUtil.nullToEmpty(redirectUri),
                StrUtil.nullToEmpty(state),
                StrUtil.nullToEmpty(codeChallenge),
                StrUtil.nullToEmpty(codeChallengeMethod),
                StrUtil.nullToEmpty(resource),
                scopeKeys);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private static OAuth2GrantTypeEnum getGrantTypeEnum(String responseType) {
        if (StrUtil.equals(responseType, "code")) {
            return OAuth2GrantTypeEnum.AUTHORIZATION_CODE;
        }
        throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "response_type 参数值只允许 code");
    }

    private static void validateRedirectUri(OAuth2ClientDO client, String redirectUri) {
        if (StrUtil.isBlank(redirectUri) || !CollUtil.contains(client.getRedirectUris(), redirectUri)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "redirect_uri 参数值不匹配");
        }
    }

    private static void validatePkceAndResource(OAuth2GrantTypeEnum grantTypeEnum, OAuth2ClientDO client,
                                                String codeChallenge, String codeChallengeMethod, String resource,
                                                String expectedResource) {
        if (grantTypeEnum != OAuth2GrantTypeEnum.AUTHORIZATION_CODE) {
            return;
        }
        McpOAuthPkceVerifier.validateCodeChallenge(codeChallenge, codeChallengeMethod);
        if (!McpOAuthResourceValidator.matches(resource, client, expectedResource)) {
            throw exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "resource 参数值不匹配");
        }
    }

    private static Integer getUserType() {
        return UserTypeEnum.ADMIN.getValue();
    }

}
