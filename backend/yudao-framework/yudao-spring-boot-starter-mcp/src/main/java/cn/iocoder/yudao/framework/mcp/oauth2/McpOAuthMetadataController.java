package cn.iocoder.yudao.framework.mcp.oauth2;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpServerProperties;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP OAuth discovery metadata。
 *
 * @author bujidao
 */
@RestController
@PermitAll
@RequiredArgsConstructor
public class McpOAuthMetadataController {

    private static final String PROTECTED_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";
    private static final String AUTHORIZATION_SERVER_METADATA_PATH = "/.well-known/oauth-authorization-server";

    private final YudaoMcpServerProperties properties;
    private final ObjectProvider<WebProperties> webPropertiesProvider;

    @GetMapping({PROTECTED_RESOURCE_METADATA_PATH, PROTECTED_RESOURCE_METADATA_PATH + "/**"})
    public Map<String, Object> protectedResourceMetadata(HttpServletRequest request) {
        String origin = origin(request);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", resourceUri(origin));
        metadata.put("authorization_servers", List.of(issuer(origin)));
        metadata.put("scopes_supported", properties.getRequiredScopes());
        metadata.put("bearer_methods_supported", List.of("header"));
        metadata.put("resource_name", properties.getName());
        if (StrUtil.isNotBlank(properties.getResourceDocumentation())) {
            metadata.put("resource_documentation", absolute(origin, properties.getResourceDocumentation()));
        }
        return metadata;
    }

    @GetMapping(AUTHORIZATION_SERVER_METADATA_PATH)
    public Map<String, Object> authorizationServerMetadata(HttpServletRequest request) {
        String origin = origin(request);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer(origin));
        metadata.put("authorization_endpoint", authorizationEndpoint(origin));
        metadata.put("token_endpoint", tokenEndpoint(origin));
        metadata.put("revocation_endpoint", revocationEndpoint(origin));
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_basic"));
        metadata.put("scopes_supported", properties.getRequiredScopes());
        return metadata;
    }

    private String resourceUri(String origin) {
        return StrUtil.isNotBlank(properties.getPublicResourceUri())
                ? properties.getPublicResourceUri() : absolute(origin, properties.getEndpoint());
    }

    private String issuer(String origin) {
        return StrUtil.isNotBlank(properties.getAuthorizationServerIssuer())
                ? properties.getAuthorizationServerIssuer() : origin;
    }

    private String authorizationEndpoint(String origin) {
        if (StrUtil.isNotBlank(properties.getAuthorizationEndpoint())) {
            return absolute(origin, properties.getAuthorizationEndpoint());
        }
        WebProperties webProperties = webPropertiesProvider.getIfAvailable();
        String adminUiUrl = webProperties == null || webProperties.getAdminUi() == null
                ? null : webProperties.getAdminUi().getUrl();
        return absolute(StrUtil.blankToDefault(adminUiUrl, origin), "/sso");
    }

    private String tokenEndpoint(String origin) {
        if (StrUtil.isNotBlank(properties.getTokenEndpoint())) {
            return absolute(origin, properties.getTokenEndpoint());
        }
        return absolute(origin, adminApiPrefix() + "/system/oauth2/token");
    }

    private String revocationEndpoint(String origin) {
        if (StrUtil.isNotBlank(properties.getRevocationEndpoint())) {
            return absolute(origin, properties.getRevocationEndpoint());
        }
        return tokenEndpoint(origin);
    }

    private String adminApiPrefix() {
        WebProperties webProperties = webPropertiesProvider.getIfAvailable();
        if (webProperties == null || webProperties.getAdminApi() == null
                || StrUtil.isBlank(webProperties.getAdminApi().getPrefix())) {
            return "/admin-api";
        }
        return webProperties.getAdminApi().getPrefix();
    }

    private static String absolute(String origin, String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return origin + (value.startsWith("/") ? value : "/" + value);
    }

    private static String origin(HttpServletRequest request) {
        return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath(null)
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
    }

}
