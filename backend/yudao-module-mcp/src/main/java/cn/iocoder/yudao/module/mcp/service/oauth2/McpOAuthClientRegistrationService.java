package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationReqVO;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationRespVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2ClientMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2GrantTypeEnum;
import cn.iocoder.yudao.module.system.util.oauth2.OAuth2Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MCP OAuth Dynamic Client Registration Service。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
public class McpOAuthClientRegistrationService {

    private static final String TOKEN_ENDPOINT_AUTH_METHOD_NONE = "none";
    private static final List<String> SUPPORTED_GRANT_TYPES = List.of(
            OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType(),
            OAuth2GrantTypeEnum.REFRESH_TOKEN.getGrantType());
    private static final List<String> SUPPORTED_RESPONSE_TYPES = List.of("code");
    private static final List<String> DISALLOWED_PRIVATE_USE_SCHEMES = List.of(
            "http", "https", "javascript", "data", "file", "ftp", "mailto");

    private final McpOAuthProperties properties;
    private final OAuth2ClientMapper oauth2ClientMapper;
    private final CacheManager cacheManager;

    public McpOAuthClientRegistrationRespVO register(McpOAuthClientRegistrationReqVO reqVO, String resourceUri) {
        if (!properties.isDynamicClientRegistrationEnabled()) {
            throw new McpOAuthException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "invalid_request", "Dynamic client registration is disabled");
        }
        if (reqVO == null) {
            throw McpOAuthException.invalidRequest("request body is required");
        }

        List<String> redirectUris = normalizeList(reqVO.getRedirectUris());
        if (CollUtil.isEmpty(redirectUris) || !redirectUris.stream().allMatch(this::isAllowedRedirectUri)) {
            throw new McpOAuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "invalid_redirect_uri", "redirect_uris contains unsupported URI");
        }
        List<String> grantTypes = normalizeGrantTypes(reqVO.getGrantTypes());
        List<String> responseTypes = normalizeResponseTypes(reqVO.getResponseTypes());
        String tokenEndpointAuthMethod = StrUtil.blankToDefault(reqVO.getTokenEndpointAuthMethod(),
                TOKEN_ENDPOINT_AUTH_METHOD_NONE);
        if (!StrUtil.equals(tokenEndpointAuthMethod, TOKEN_ENDPOINT_AUTH_METHOD_NONE)) {
            throw McpOAuthException.invalidRequest(
                    "only public clients with token_endpoint_auth_method=none are supported");
        }
        List<String> scopes = normalizeScopes(reqVO.getScope());

        OAuth2ClientDO client = createClient(reqVO, redirectUris, grantTypes, scopes, resourceUri);
        return buildResp(reqVO, client, grantTypes, responseTypes, scopes, tokenEndpointAuthMethod);
    }

    private OAuth2ClientDO createClient(McpOAuthClientRegistrationReqVO reqVO, List<String> redirectUris,
                                        List<String> grantTypes, List<String> scopes, String resourceUri) {
        OAuth2ClientDO client = new OAuth2ClientDO()
                .setClientId(generateClientId())
                .setSecret("")
                .setName(truncate(StrUtil.blankToDefault(reqVO.getClientName(), properties.getDefaultClientName()), 255))
                .setLogo(truncate(StrUtil.blankToDefault(reqVO.getLogoUri(), properties.getDefaultLogo()), 255))
                .setDescription("Dynamically registered MCP OAuth client")
                .setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setRedirectUris(redirectUris)
                .setAuthorizedGrantTypes(grantTypes)
                .setScopes(scopes)
                .setAutoApproveScopes(List.of())
                .setAuthorities(List.of())
                .setResourceIds(List.of())
                .setAdditionalInformation(JsonUtils.toJsonString(buildAdditionalInformation(reqVO, resourceUri)))
                .setAccessTokenValiditySeconds(properties.getAccessTokenValiditySeconds())
                .setRefreshTokenValiditySeconds(properties.getRefreshTokenValiditySeconds());
        oauth2ClientMapper.insert(client);
        clearClientCache();
        return client;
    }

    private static Map<String, Object> buildAdditionalInformation(McpOAuthClientRegistrationReqVO reqVO,
                                                                  String resourceUri) {
        Map<String, Object> additionalInformation = new LinkedHashMap<>();
        additionalInformation.put("mcp_dynamic_client_registration", true);
        additionalInformation.put("dynamic_client_registration", true);
        additionalInformation.put("token_endpoint_auth_method", TOKEN_ENDPOINT_AUTH_METHOD_NONE);
        additionalInformation.put("public_client", true);
        additionalInformation.put("require_pkce", true);
        additionalInformation.put("mcp_resource", resourceUri);
        additionalInformation.put("client_uri", reqVO.getClientUri());
        additionalInformation.put("application_type", StrUtil.blankToDefault(reqVO.getApplicationType(), "native"));
        return additionalInformation;
    }

    private McpOAuthClientRegistrationRespVO buildResp(McpOAuthClientRegistrationReqVO reqVO,
                                                       OAuth2ClientDO client,
                                                       List<String> grantTypes,
                                                       List<String> responseTypes,
                                                       List<String> scopes,
                                                       String tokenEndpointAuthMethod) {
        return new McpOAuthClientRegistrationRespVO()
                .setClientId(client.getClientId())
                .setClientIdIssuedAt(Instant.now().getEpochSecond())
                .setClientName(client.getName())
                .setRedirectUris(client.getRedirectUris())
                .setGrantTypes(grantTypes)
                .setResponseTypes(responseTypes)
                .setTokenEndpointAuthMethod(tokenEndpointAuthMethod)
                .setApplicationType(StrUtil.blankToDefault(reqVO.getApplicationType(), "native"))
                .setScope(OAuth2Utils.buildScopeStr(scopes));
    }

    private String generateClientId() {
        for (int i = 0; i < 5; i++) {
            String clientId = properties.getClientIdPrefix() + IdUtil.fastSimpleUUID();
            if (oauth2ClientMapper.selectByClientId(clientId) == null) {
                return clientId;
            }
        }
        return properties.getClientIdPrefix() + IdUtil.fastSimpleUUID();
    }

    private List<String> normalizeScopes(String scope) {
        List<String> scopes = normalizeList(OAuth2Utils.buildScopes(scope));
        if (CollUtil.isEmpty(scopes)) {
            return properties.getDefaultScopes();
        }
        if (!CollUtil.containsAll(properties.getDefaultScopes(), scopes)) {
            throw McpOAuthException.invalidScope("requested scope is not allowed");
        }
        return scopes;
    }

    private static List<String> normalizeGrantTypes(List<String> grantTypes) {
        List<String> normalized = normalizeList(grantTypes);
        if (CollUtil.isEmpty(normalized)) {
            normalized = List.of(OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType());
        }
        if (!normalized.contains(OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType())
                || !CollUtil.containsAll(SUPPORTED_GRANT_TYPES, normalized)) {
            throw McpOAuthException.invalidRequest(
                    "grant_types must be authorization_code and optional refresh_token");
        }
        return normalized;
    }

    private static List<String> normalizeResponseTypes(List<String> responseTypes) {
        List<String> normalized = normalizeList(responseTypes);
        if (CollUtil.isEmpty(normalized)) {
            return SUPPORTED_RESPONSE_TYPES;
        }
        if (!CollUtil.containsAll(SUPPORTED_RESPONSE_TYPES, normalized)) {
            throw McpOAuthException.invalidRequest("response_types must be code");
        }
        return normalized;
    }

    private static List<String> normalizeList(List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .distinct()
                .toList();
    }

    private boolean isAllowedRedirectUri(String value) {
        if (StrUtil.isBlank(value) || value.length() > 255) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            return false;
        }
        if (StrUtil.isBlank(uri.getScheme()) || uri.getFragment() != null) {
            return false;
        }
        if (properties.getAllowedRedirectUriPrefixes().stream().anyMatch(value::startsWith)) {
            return true;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (StrUtil.equalsAny(scheme, "http", "https")) {
            String host = uri.getHost();
            return uri.getUserInfo() == null && StrUtil.isNotBlank(host)
                    && properties.getLocalRedirectHosts().stream()
                    .anyMatch(allowedHost -> StrUtil.equalsIgnoreCase(allowedHost, host));
        }
        return properties.isAllowPrivateUseUriSchemeRedirects()
                && scheme.matches("[a-z][a-z0-9+.-]{2,63}")
                && !DISALLOWED_PRIVATE_USE_SCHEMES.contains(scheme)
                && StrUtil.isNotBlank(uri.getSchemeSpecificPart());
    }

    private void clearClientCache() {
        Cache cache = cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT);
        if (cache != null) {
            cache.clear();
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
