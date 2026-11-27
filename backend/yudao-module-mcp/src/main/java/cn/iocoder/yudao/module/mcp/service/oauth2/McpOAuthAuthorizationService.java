package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationRequestRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthPkceVerifier;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRedirectUriMatcher;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceValidator;
import cn.iocoder.yudao.module.system.convert.oauth2.OAuth2OpenConvert;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ApproveDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
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
    private final McpOAuthClientResolver clientResolver;
    private final McpOAuthProperties properties;

    public OAuth2OpenAuthorizeInfoRespVO getAuthorizeInfo(String clientId) {
        try {
            return getAuthorizeInfoInternal(clientId);
        } catch (McpOAuthException exception) {
            throw toBadRequest(exception);
        }
    }

    private OAuth2OpenAuthorizeInfoRespVO getAuthorizeInfoInternal(String clientId) {
        OAuth2ClientDO client = clientResolver.isCimdClientId(clientId)
                ? resolveCimdClient(clientId, null)
                : oauth2ClientService.validOAuthClientFromCache(clientId);
        List<OAuth2ApproveDO> approves = oauth2ApproveService.getApproveList(
                SecurityFrameworkUtils.getLoginUserId(), getUserType(), clientId);
        return OAuth2OpenConvert.INSTANCE.convert(client, approves);
    }

    public String approveOrDeny(String responseType, String clientId, String scope, String redirectUri,
                                Boolean autoApprove, String state, String codeChallenge,
                                String codeChallengeMethod, String resource, String expectedResource) {
        try {
            return approveOrDenyInternal(responseType, clientId, scope, redirectUri, autoApprove, state,
                    codeChallenge, codeChallengeMethod, resource, expectedResource);
        } catch (McpOAuthException exception) {
            throw toBadRequest(exception);
        }
    }

    private String approveOrDenyInternal(String responseType, String clientId, String scope, String redirectUri,
                                Boolean autoApprove, String state, String codeChallenge,
                                String codeChallengeMethod, String resource, String expectedResource) {
        @SuppressWarnings("unchecked")
        Map<String, Boolean> scopes = JsonUtils.parseObject(scope, Map.class);
        scopes = ObjectUtil.defaultIfNull(scopes, Collections.emptyMap());

        OAuth2GrantTypeEnum grantTypeEnum = getGrantTypeEnum(responseType);
        boolean cimdClient = clientResolver.isCimdClientId(clientId);
        OAuth2ClientDO client;
        if (cimdClient) {
            // URL 形态：上游查库查不到，改走 Resolver（带 redirect_uri 做成员校验）+ 本地 grant / scope 校验
            client = resolveCimdClient(clientId, redirectUri);
            validateCimdGrantAndScopes(client, grantTypeEnum, scopes.keySet());
        } else {
            client = oauth2ClientService.validOAuthClientFromCache(clientId, null,
                    grantTypeEnum.getGrantType(), scopes.keySet(), null);
        }
        validateRedirectUri(client, redirectUri);
        validatePkceAndResource(grantTypeEnum, client, codeChallenge, codeChallengeMethod, resource, expectedResource);

        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (Boolean.TRUE.equals(autoApprove)) {
            boolean preApproved = cimdClient
                    ? checkCimdPreApproval(userId, scopes.keySet(), clientId)
                    : oauth2ApproveService.checkForPreApproval(userId, getUserType(), clientId, scopes.keySet());
            if (!preApproved) {
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
                // 空白 resource 归一化为 null：与兑换入口一致，否则授权 ""、兑换不带
                // 会在 validateAuthorizationCodeBinding 的 equals 比对里误判 invalid_grant。
                .setResource(StrUtil.blankToNull(resource))
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

    /**
     * 把 McpOAuthException 统一转成授权端点现有的 ServiceException 形态
     * （BAD_REQUEST + {@code <oauth_error>: <description>}），避免未经
     * GlobalExceptionHandler 处理的 McpOAuthException 在授权接口上变成 500。
     * 覆盖整个方法体：resolveCimdClient 之外的 McpOAuthException 来源（如
     * validatePkceAndResource 里 McpOAuthPkceVerifier 抛的 invalid_request）也在此转换。
     * token 端点由控制器直接接 McpOAuthException，不走这里。
     */
    private static ServiceException toBadRequest(McpOAuthException exception) {
        return exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                exception.getError() + ": " + exception.getDescription());
    }

    /**
     * 解析 CIMD 客户端，并把 Resolver 的 {@link McpOAuthException#invalidClient(String)}
     * 转成授权端点现有的 ServiceException 形态（BAD_REQUEST + {@code invalid_client:} 前缀），
     * 避免未经 GlobalExceptionHandler 处理的 McpOAuthException 在授权接口上变成 500。
     * token 端点由控制器直接接 McpOAuthException，不走这里。
     */
    private OAuth2ClientDO resolveCimdClient(String clientId, String redirectUri) {
        try {
            return clientResolver.resolve(clientId, redirectUri);
        } catch (McpOAuthException exception) {
            throw toBadRequest(exception);
        }
    }

    /**
     * CIMD 客户端的 grant / scope 本地校验，与上游
     * {@code OAuth2ClientServiceImpl#validOAuthClientFromCache} 的同一段逻辑、同一错误码对齐：
     * grant 必须在合成客户端的 authorizedGrantTypes 内，请求 scope 必须是合成 scopes 的子集。
     */
    private static void validateCimdGrantAndScopes(OAuth2ClientDO client, OAuth2GrantTypeEnum grantType,
                                                   Collection<String> scopes) {
        if (!CollUtil.contains(client.getAuthorizedGrantTypes(), grantType.getGrantType())) {
            throw exception(ErrorCodeConstants.OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS);
        }
        if (CollUtil.isNotEmpty(scopes) && !CollUtil.containsAll(client.getScopes(), scopes)) {
            throw exception(ErrorCodeConstants.OAUTH2_CLIENT_SCOPE_OVER);
        }
    }

    /**
     * CIMD 客户端的预批准判定：不上游的 checkForPreApproval（它会先查库校验客户端，
     * CIMD 客户端不在库里，会误报“客户端不存在”）。合成客户端的 autoApproveScopes 为空，
     * 上游第一步恒不通过，实际判定只看用户已存的批准记录，与上游第二步一致。
     */
    private boolean checkCimdPreApproval(Long userId, Collection<String> requestedScopes, String clientId) {
        List<OAuth2ApproveDO> approves = oauth2ApproveService.getApproveList(userId, getUserType(), clientId);
        Set<String> approvedScopes = approves.stream()
                .filter(approve -> Boolean.TRUE.equals(approve.getApproved()))
                .map(OAuth2ApproveDO::getScope)
                .collect(Collectors.toSet());
        return approvedScopes.containsAll(requestedScopes);
    }

    private void validateRedirectUri(OAuth2ClientDO client, String redirectUri) {
        if (!McpOAuthRedirectUriMatcher.matches(redirectUri, client.getRedirectUris(),
                properties.getLocalRedirectHosts())) {
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
