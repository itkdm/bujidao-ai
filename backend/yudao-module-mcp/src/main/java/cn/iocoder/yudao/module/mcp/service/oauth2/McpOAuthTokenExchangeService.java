package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.mcp.dal.redis.oauth2.McpOAuthAuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthPkceVerifier;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceValidator;
import cn.iocoder.yudao.module.system.convert.oauth2.OAuth2OpenConvert;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2RefreshTokenMapper;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2GrantTypeEnum;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2CodeService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * MCP OAuth token endpoint Service。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
public class McpOAuthTokenExchangeService {

    private final OAuth2ClientService oauth2ClientService;
    private final OAuth2CodeService oauth2CodeService;
    private final OAuth2TokenService oauth2TokenService;
    private final OAuth2RefreshTokenMapper oauth2RefreshTokenMapper;
    private final McpOAuthAuthorizationCodeExtraRedisDAO codeExtraRedisDAO;

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
                () -> oauth2TokenService.createAccessToken(codeDO.getUserId(), codeDO.getUserType(),
                        codeDO.getClientId(), codeDO.getScopes()));
        return OAuth2OpenConvert.INSTANCE.convert(accessToken);
    }

    public OAuth2OpenAccessTokenRespVO refreshAccessToken(String clientId, String refreshToken) {
        validatePublicClient(clientId, OAuth2GrantTypeEnum.REFRESH_TOKEN.getGrantType(), null);
        OAuth2RefreshTokenDO refreshTokenDO = oauth2RefreshTokenMapper.selectByRefreshToken(refreshToken);
        if (refreshTokenDO == null || ObjectUtil.notEqual(clientId, refreshTokenDO.getClientId())) {
            throw McpOAuthException.invalidGrant("refresh_token is invalid");
        }
        OAuth2AccessTokenDO accessToken = TenantUtils.execute(refreshTokenDO.getTenantId(),
                () -> oauth2TokenService.refreshAccessToken(refreshToken, clientId));
        return OAuth2OpenConvert.INSTANCE.convert(accessToken);
    }

    public boolean revokeToken(String clientId, String accessToken) {
        validatePublicClient(clientId, null, null);
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.getAccessToken(accessToken);
        if (accessTokenDO == null || ObjectUtil.notEqual(clientId, accessTokenDO.getClientId())) {
            return false;
        }
        return oauth2TokenService.removeAccessToken(accessToken) != null;
    }

    private OAuth2ClientDO validatePublicClient(String clientId, String grantType, Collection<String> scopes) {
        if (StrUtil.isBlank(clientId)) {
            throw McpOAuthException.invalidClient("client_id is required");
        }
        OAuth2ClientDO client = oauth2ClientService.validOAuthClientFromCache(clientId, null, grantType, scopes, null);
        if (!isPublicClient(client)) {
            throw McpOAuthException.invalidClient("only public clients are supported");
        }
        return client;
    }

    private static void validateRedirectUri(OAuth2ClientDO client, String redirectUri) {
        if (StrUtil.isBlank(redirectUri) || !client.getRedirectUris().contains(redirectUri)) {
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
