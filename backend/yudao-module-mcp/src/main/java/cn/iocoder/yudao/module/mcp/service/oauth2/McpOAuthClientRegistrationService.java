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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class McpOAuthClientRegistrationService {

    private static final String TOKEN_ENDPOINT_AUTH_METHOD_NONE = "none";
    private static final List<String> SUPPORTED_GRANT_TYPES = List.of(
            OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType(),
            OAuth2GrantTypeEnum.REFRESH_TOKEN.getGrantType());
    private static final List<String> SUPPORTED_RESPONSE_TYPES = List.of("code");
    // 私有 scheme 黑名单：注册时拒绝这些 scheme 作 redirect_uri。
    // 原因：授权通过后浏览器会直接导航到注册的 redirect_uri（带 code），
    // 若 scheme 对应操作系统/浏览器的协议处理器，攻击者只需骗用户点一次
    // “同意”，就能触发本地程序（如 ms-msdt 的 Follina、search-ms 的资源管理器搜索）。
    // 注意：黑名单永远补不完，新协议处理器还会出现；这里只堵已知的危险项，
    // 残余风险须在部署时接受：不要把本服务端暴露给不可信网络，或改用
    // allowedRedirectUriPrefixes 由管理员逐个放行。不要改成白名单：开放 DCR
    // 的意义就是任意桌面客户端都能注册，而合法应用 scheme（如 workbuddy-ai://）
    // 无法事先枚举。
    private static final List<String> DISALLOWED_PRIVATE_USE_SCHEMES = List.of(
            // 通用危险 scheme
            "http", "https", "javascript", "data", "file", "ftp", "mailto", "tel", "sms",
            "vbscript", "intent",
            // Windows 协议处理器：曾被用于钓鱼 / 远程执行
            "ms-msdt", "search-ms", "ms-officeapp", "ms-excel", "ms-word", "ms-powerpoint",
            "ms-access", "ms-publisher", "ms-visio", "ms-project", "ms-settings",
            "ms-screenclip", "ms-gamingoverlay", "ms-browser-extension", "ms-calculator",
            "calculator", "ms-people", "ms-call", "ms-sms", "ms-chat", "ms-store",
            "ms-windows-store",
            // 浏览器启动 scheme：会绕过 http(s) 仅回环的限制，把 code 送到攻击者 URL
            "microsoft-edge");

    private final McpOAuthProperties properties;
    private final OAuth2ClientMapper oauth2ClientMapper;
    private final CacheManager cacheManager;

    public McpOAuthClientRegistrationRespVO register(McpOAuthClientRegistrationReqVO reqVO, String resourceUri) {
        try {
            return registerInternal(reqVO, resourceUri);
        } catch (McpOAuthException exception) {
            // 审计：只记错误码，不记请求里的 redirect_uri / client_name（客户端自称内容）。
            log.warn("[register][error({})] dynamic client registration rejected", exception.getError());
            throw exception;
        }
    }

    private McpOAuthClientRegistrationRespVO registerInternal(McpOAuthClientRegistrationReqVO reqVO,
                                                             String resourceUri) {
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
        // 审计：client_id 是服务端签发的非敏感编号，只记它，不记 client_name / redirect_uri。
        log.info("[register][clientId({})] dynamic client registered", client.getClientId());
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
