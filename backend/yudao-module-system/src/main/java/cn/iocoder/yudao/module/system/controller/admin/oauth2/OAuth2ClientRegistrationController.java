package cn.iocoder.yudao.module.system.controller.admin.oauth2;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2ClientRegistrationReqVO;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2ClientRegistrationRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2GrantTypeEnum;
import cn.iocoder.yudao.module.system.framework.oauth2.config.OAuth2DynamicClientRegistrationProperties;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.dto.OAuth2DynamicClientRegistrationCreateReqDTO;
import cn.iocoder.yudao.module.system.util.oauth2.OAuth2Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OAuth2 Dynamic Client Registration。
 *
 * @author bujidao
 */
@Tag(name = "管理后台 - OAuth2.0 动态客户端注册")
@RestController
@RequestMapping("/system/oauth2")
public class OAuth2ClientRegistrationController {

    private static final String TOKEN_ENDPOINT_AUTH_METHOD_NONE = "none";
    private static final List<String> SUPPORTED_GRANT_TYPES = List.of(
            OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType(),
            OAuth2GrantTypeEnum.REFRESH_TOKEN.getGrantType());
    private static final List<String> SUPPORTED_RESPONSE_TYPES = List.of("code");
    private static final List<String> DISALLOWED_PRIVATE_USE_SCHEMES = List.of(
            "http", "https", "javascript", "data", "file", "ftp", "mailto");

    @Resource
    private OAuth2ClientService oauth2ClientService;
    @Resource
    private OAuth2DynamicClientRegistrationProperties properties;

    @PostMapping("/register")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "动态注册 OAuth2 客户端", description = "返回标准 OAuth2 Dynamic Client Registration 响应，供 MCP 等客户端使用")
    public ResponseEntity<?> register(@RequestBody(required = false) OAuth2ClientRegistrationReqVO reqVO) {
        if (!properties.isEnabled()) {
            return error(HttpStatus.NOT_FOUND, "invalid_request", "Dynamic client registration is disabled");
        }
        if (reqVO == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_client_metadata", "request body is required");
        }

        List<String> redirectUris = normalizeList(reqVO.getRedirectUris());
        if (CollUtil.isEmpty(redirectUris) || !redirectUris.stream().allMatch(this::isAllowedRedirectUri)) {
            return error(HttpStatus.BAD_REQUEST, "invalid_redirect_uri", "redirect_uris contains unsupported URI");
        }
        List<String> grantTypes = normalizeGrantTypes(reqVO.getGrantTypes());
        if (grantTypes == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_client_metadata", "grant_types must be authorization_code and optional refresh_token");
        }
        List<String> responseTypes = normalizeResponseTypes(reqVO.getResponseTypes());
        if (responseTypes == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_client_metadata", "response_types must be code");
        }
        String tokenEndpointAuthMethod = StrUtil.blankToDefault(reqVO.getTokenEndpointAuthMethod(),
                TOKEN_ENDPOINT_AUTH_METHOD_NONE);
        if (!StrUtil.equals(tokenEndpointAuthMethod, TOKEN_ENDPOINT_AUTH_METHOD_NONE)) {
            return error(HttpStatus.BAD_REQUEST, "invalid_client_metadata", "only public clients with token_endpoint_auth_method=none are supported");
        }
        List<String> scopes = normalizeScopes(reqVO.getScope());
        if (scopes == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_scope", "requested scope is not allowed");
        }

        OAuth2ClientDO client = oauth2ClientService.createDynamicOAuth2Client(buildCreateReq(
                reqVO, redirectUris, grantTypes, scopes));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(buildResp(reqVO, client, grantTypes, responseTypes, scopes, tokenEndpointAuthMethod));
    }

    private OAuth2DynamicClientRegistrationCreateReqDTO buildCreateReq(OAuth2ClientRegistrationReqVO reqVO,
                                                                       List<String> redirectUris,
                                                                       List<String> grantTypes,
                                                                       List<String> scopes) {
        String clientName = truncate(StrUtil.blankToDefault(reqVO.getClientName(),
                properties.getDefaultClientName()), 255);
        String logoUri = truncate(StrUtil.blankToDefault(reqVO.getLogoUri(), properties.getDefaultLogo()), 255);
        Map<String, Object> additionalInformation = new LinkedHashMap<>();
        additionalInformation.put("dynamic_client_registration", true);
        additionalInformation.put("token_endpoint_auth_method", TOKEN_ENDPOINT_AUTH_METHOD_NONE);
        additionalInformation.put("public_client", true);
        additionalInformation.put("require_pkce", true);
        additionalInformation.put("client_uri", reqVO.getClientUri());
        additionalInformation.put("application_type", StrUtil.blankToDefault(reqVO.getApplicationType(), "native"));

        return new OAuth2DynamicClientRegistrationCreateReqDTO()
                .setClientId(generateClientId())
                .setClientName(clientName)
                .setLogoUri(logoUri)
                .setDescription("Dynamically registered OAuth2 client")
                .setRedirectUris(redirectUris)
                .setAuthorizedGrantTypes(grantTypes)
                .setScopes(scopes)
                .setAutoApproveScopes(scopes)
                .setResourceIds(List.of())
                .setAdditionalInformation(JsonUtils.toJsonString(additionalInformation))
                .setAccessTokenValiditySeconds(properties.getAccessTokenValiditySeconds())
                .setRefreshTokenValiditySeconds(properties.getRefreshTokenValiditySeconds());
    }

    private OAuth2ClientRegistrationRespVO buildResp(OAuth2ClientRegistrationReqVO reqVO,
                                                     OAuth2ClientDO client,
                                                     List<String> grantTypes,
                                                     List<String> responseTypes,
                                                     List<String> scopes,
                                                     String tokenEndpointAuthMethod) {
        return new OAuth2ClientRegistrationRespVO()
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
            if (oauth2ClientService.getOAuth2ClientFromCache(clientId) == null) {
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
        return CollUtil.containsAll(properties.getDefaultScopes(), scopes) ? scopes : null;
    }

    private static List<String> normalizeGrantTypes(List<String> grantTypes) {
        List<String> normalized = normalizeList(grantTypes);
        if (CollUtil.isEmpty(normalized)) {
            normalized = List.of(OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType());
        }
        if (!normalized.contains(OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType())) {
            return null;
        }
        return CollUtil.containsAll(SUPPORTED_GRANT_TYPES, normalized) ? normalized : null;
    }

    private static List<String> normalizeResponseTypes(List<String> responseTypes) {
        List<String> normalized = normalizeList(responseTypes);
        if (CollUtil.isEmpty(normalized)) {
            return SUPPORTED_RESPONSE_TYPES;
        }
        return CollUtil.containsAll(SUPPORTED_RESPONSE_TYPES, normalized) ? normalized : null;
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

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String description) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of("error", error, "error_description", description));
    }

}
