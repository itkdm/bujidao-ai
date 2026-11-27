package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthPkceVerifier;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRedirectUriMatcher;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceValidator;
import cn.iocoder.yudao.module.system.convert.oauth2.OAuth2OpenConvert;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2AccessTokenMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2GrantTypeEnum;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2CodeService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP OAuth token endpoint Service。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpOAuthTokenExchangeService {

    private final OAuth2ClientService oauth2ClientService;
    private final OAuth2CodeService oauth2CodeService;
    private final OAuth2TokenService oauth2TokenService;
    private final OAuth2RefreshTokenMapper oauth2RefreshTokenMapper;
    private final OAuth2AccessTokenMapper oauth2AccessTokenMapper;
    private final OAuth2AccessTokenRedisDAO oauth2AccessTokenRedisDAO;
    private final McpOAuthAuthorizationCodeExtraRedisDAO codeExtraRedisDAO;
    private final McpOAuthClientResolver clientResolver;
    private final McpOAuthProperties properties;

    public OAuth2OpenAccessTokenRespVO exchangeAuthorizationCode(String clientId, String code, String redirectUri,
                                                                 String codeVerifier, String resource,
                                                                 String expectedResource) {
        OAuth2ClientDO client = validatePublicClient(clientId,
                OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType(), null);
        validateRedirectUri(client, redirectUri);
        if (!McpOAuthResourceValidator.matches(resource, client, expectedResource)) {
            throw McpOAuthException.invalidGrant("resource is invalid");
        }
        McpOAuthAuthorizationCodeExtraDO codeExtra = codeExtraRedisDAO.get(code);
        if (codeExtra == null) {
            throw McpOAuthException.invalidGrant("authorization code is invalid");
        }

        OAuth2CodeDO codeDO = oauth2CodeService.consumeAuthorizationCode(code);
        if (codeDO == null) {
            throw McpOAuthException.invalidGrant("authorization code is invalid");
        }
        validateAuthorizationCode(codeDO, codeExtra, clientId, redirectUri, resource, codeVerifier);
        codeExtraRedisDAO.delete(code);

        OAuth2AccessTokenDO accessToken = TenantUtils.execute(codeExtra.getTenantId(),
                () -> clientResolver.isCimdClientId(clientId)
                        ? oauth2TokenService.createAccessToken(codeDO.getUserId(), codeDO.getUserType(),
                                client, codeDO.getScopes())
                        : oauth2TokenService.createAccessToken(codeDO.getUserId(), codeDO.getUserType(),
                                codeDO.getClientId(), codeDO.getScopes()));
        // 审计：CIMD 兑换成功只记 client_id 的 host，不记 token / code 等敏感值。
        if (clientResolver.isCimdClientId(clientId)) {
            log.info("[exchange][host({})] CIMD client token exchange succeeded", hostOf(clientId));
        }
        return OAuth2OpenConvert.INSTANCE.convert(accessToken);
    }

    public OAuth2OpenAccessTokenRespVO refreshAccessToken(String clientId, String refreshToken) {
        // 先查刷新令牌：垃圾 refresh_token 不应触发 CIMD 文档拉取。
        // 注意与 exchangeAuthorizationCode 的顺序不同：授权码消费是一次性的，必须先解析
        // 客户端、校验通过后再消费授权码，否则无效客户端也能把授权码消耗掉。
        OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken);
        if (refreshTokenDO == null || ObjectUtil.notEqual(clientId, refreshTokenDO.getClientId())) {
            throw McpOAuthException.invalidGrant("refresh_token is invalid");
        }
        OAuth2ClientDO client = validatePublicClient(clientId, OAuth2GrantTypeEnum.REFRESH_TOKEN.getGrantType(), null);
        OAuth2AccessTokenDO accessToken = TenantUtils.execute(refreshTokenDO.getTenantId(),
                () -> clientResolver.isCimdClientId(clientId)
                        ? oauth2TokenService.refreshAccessToken(refreshToken, client)
                        : oauth2TokenService.refreshAccessToken(refreshToken, clientId));
        // 审计：CIMD 刷新成功只记 client_id 的 host，不记 token 等敏感值。
        if (clientResolver.isCimdClientId(clientId)) {
            log.info("[exchange][host({})] CIMD client token refresh succeeded", hostOf(clientId));
        }
        return OAuth2OpenConvert.INSTANCE.convert(accessToken);
    }

    /**
     * 吊销令牌（RFC 7009）：access token 与 refresh token 都必须能吊销。
     * 先按 access token 表查，查不到再按 refresh token 表查（不新增
     * {@code token_type_hint} 参数：两表查找已足够区分，调用方不传 hint 也能吊销）。
     * 注意：传入 refresh token 时 {@code getAccessToken} 的回退会把它拼成 access
     * token DO（N1），故上一步的 clientId 校验能通过；但上游
     * {@code removeAccessToken} 只按 access 表删，返回 null，此时必须走下面的
     * refresh 分支，否则 refresh 在 30 天内仍可换新 access token。
     * 令牌不存在时返回 false，控制器仍回 HTTP 200（RFC 7009 §2.2.1：不向调用方
     * 透露令牌是否存在）。注意 {@code getAccessToken} 对过期 refresh 返回 null，
     * 此时也落到 refresh 分支：过期行已不可用，但顺手删掉、不留残留。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeToken(String clientId, String accessToken) {
        if (StrUtil.isBlank(clientId)) {
            throw McpOAuthException.invalidClient("client_id is required");
        }
        if (!clientResolver.isCimdClientId(clientId)) {
            // DB 分支：保持走 validatePublicClient（查库校验客户端）。
            validatePublicClient(clientId, null, null);
        }
        // CIMD 分支只做形态判定，不拉取文档：元数据 host 挂了，已发令牌仍要能吊销。
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.getAccessToken(accessToken);
        if (accessTokenDO == null) {
            // 查不到 access token：可能是 refresh token，也可能是过期 refresh；
            // refresh 分支内二次校验 clientId，未知令牌仍返回 false。
            return revokeRefreshToken(clientId, accessToken);
        }
        if (ObjectUtil.notEqual(clientId, accessTokenDO.getClientId())) {
            return false;
        }
        if (oauth2TokenService.removeAccessToken(accessToken) != null) {
            return true;
        }
        return revokeRefreshToken(clientId, accessToken);
    }

    /**
     * 按 refresh token 吊销：删同 grant 全部 access token + refresh 行 + Redis 缓存。
     * 删除顺序与上游 {@code OAuth2TokenServiceImpl.removeAccessToken} 的级联一致
     * （先删 access 行、再删 refresh 行；Redis 键与 DB 行同删，避免 getAccessToken
     * 回退缓存的已转换 access token 继续生效）。
     */
    private boolean revokeRefreshToken(String clientId, String refreshToken) {
        OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken);
        if (refreshTokenDO == null || ObjectUtil.notEqual(clientId, refreshTokenDO.getClientId())) {
            return false;
        }
        List<OAuth2AccessTokenDO> accessTokens = oauth2AccessTokenMapper.selectListByRefreshToken(refreshToken);
        if (CollUtil.isNotEmpty(accessTokens)) {
            accessTokens.stream().map(OAuth2AccessTokenDO::getId).sorted()
                    .forEach(oauth2AccessTokenMapper::deleteById);
            oauth2AccessTokenRedisDAO.deleteList(accessTokens.stream()
                    .map(OAuth2AccessTokenDO::getAccessToken).collect(Collectors.toSet()));
        }
        oauth2RefreshTokenMapper.deleteByRefreshToken(refreshToken);
        oauth2AccessTokenRedisDAO.delete(refreshToken);
        return true;
    }

    private OAuth2ClientDO validatePublicClient(String clientId, String grantType, Collection<String> scopes) {
        if (StrUtil.isBlank(clientId)) {
            throw McpOAuthException.invalidClient("client_id is required");
        }
        OAuth2ClientDO client;
        if (clientResolver.isCimdClientId(clientId)) {
            // URL 形态：不带 redirect_uri，只拉取并校验文档；grant 校验与上游 valid* 保持一致
            client = clientResolver.resolve(clientId, null);
            validateCimdGrant(client, grantType);
        } else {
            client = oauth2ClientService.validOAuthClientFromCache(clientId, null, grantType, scopes, null);
        }
        if (!isPublicClient(client)) {
            throw McpOAuthException.invalidClient("only public clients are supported");
        }
        return client;
    }

    /** CIMD 客户端的 grant 本地校验。注意与上游 valid* 不同：上游抛 ServiceException
     * （1_002_020_003），token 控制器把它映射成 OAuth {@code unauthorized_client}；
     * 这里直接抛 {@code unauthorized_client}，与 DB 分支保持一致（RFC 6749 §5.2：
     * 客户端无权用该授权方式，不是 grant 本身无效，故不用 {@code invalid_grant}）。 */
    private static void validateCimdGrant(OAuth2ClientDO client, String grantType) {
        if (StrUtil.isNotBlank(grantType) && !client.getAuthorizedGrantTypes().contains(grantType)) {
            throw McpOAuthException.unauthorizedClient("grant_type is not supported by this client");
        }
    }

    private void validateRedirectUri(OAuth2ClientDO client, String redirectUri) {
        if (!McpOAuthRedirectUriMatcher.matches(redirectUri, client.getRedirectUris(),
                properties.getLocalRedirectHosts())) {
            throw McpOAuthException.invalidGrant("redirect_uri is invalid");
        }
    }

    private static void validateAuthorizationCode(OAuth2CodeDO codeDO, McpOAuthAuthorizationCodeExtraDO codeExtra,
                                                  String clientId, String redirectUri, String resource,
                                                  String codeVerifier) {
        if (!StrUtil.equals(clientId, codeDO.getClientId()) || !StrUtil.equals(clientId, codeExtra.getClientId())) {
            throw McpOAuthException.invalidGrant("client_id is invalid");
        }
        if (!StrUtil.equals(redirectUri, codeDO.getRedirectUri())
                || !StrUtil.equals(redirectUri, codeExtra.getRedirectUri())) {
            throw McpOAuthException.invalidGrant("redirect_uri is invalid");
        }
        if (!StrUtil.equals(resource, codeExtra.getResource())) {
            throw McpOAuthException.invalidGrant("resource is invalid");
        }
        McpOAuthPkceVerifier.verify(codeExtra.getCodeChallenge(), codeVerifier);
    }

    private static boolean isPublicClient(OAuth2ClientDO client) {
        return StrUtil.equals(getAdditionalString(client, "token_endpoint_auth_method", "tokenEndpointAuthMethod"), "none")
                || getAdditionalBoolean(client, "public_client", "publicClient");
    }

    /** 只取 host 记日志，不记完整 client_id（可能含 query 等识别信息）。 */
    private static String hostOf(String clientId) {
        try {
            URI uri = URI.create(clientId);
            return uri.getHost() == null ? "unknown" : uri.getHost();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static boolean getAdditionalBoolean(OAuth2ClientDO client, String snakeKey, String camelKey) {
        String value = getAdditionalString(client, snakeKey, camelKey);
        return StrUtil.equalsIgnoreCase(value, "true");
    }

    private static String getAdditionalString(OAuth2ClientDO client, String snakeKey, String camelKey) {
        if (client == null || StrUtil.isBlank(client.getAdditionalInformation())) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            var additionalInformation = cn.iocoder.yudao.framework.common.util.json.JsonUtils
                    .parseObject(client.getAdditionalInformation(), java.util.Map.class);
            if (additionalInformation == null) {
                return null;
            }
            Object value = additionalInformation.get(snakeKey);
            if (value == null) {
                value = additionalInformation.get(camelKey);
            }
            return value == null ? null : value.toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

}
