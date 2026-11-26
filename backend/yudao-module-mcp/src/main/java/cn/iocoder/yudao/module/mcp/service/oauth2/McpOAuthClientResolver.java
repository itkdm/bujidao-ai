package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdClientService;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdDocumentValidator;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdError;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.cimd.McpCimdMetadata;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.util.oauth2.OAuth2Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP OAuth 客户端解析器：按 client_id 形态分流，解析出一个可用的 {@link OAuth2ClientDO}。
 *
 * <ul>
 *     <li>非 URL 形态（DCR 注册的 {@code mcp-dcr-...} 之类不透明 id）：走现有上游路径
 *     {@link OAuth2ClientService#getOAuth2ClientFromCache(String)}，查不到返回 null，行为与原来一致。</li>
 *     <li>URL 形态（https、有非根 path 的绝对 URL，形状由
 *     {@link McpCimdDocumentValidator#validateClientIdUrl(String, boolean)} 判定）：调
 *     {@link McpCimdClientService} 拉取并校验 CIMD 文档，合成一个临时的 {@link OAuth2ClientDO}，
 *     不入库、不写 DB / Redis 缓存。</li>
 * </ul>
 *
 * <p>CIMD 拉取 / 校验失败时不返回 null 冒充“客户端不存在”，而是抛
 * {@link McpOAuthException#invalidClient(String)}（OAuth {@code invalid_client}），
 * 调用方据此区分“客户端不存在”与“CIMD 文档无效”；每次失败记一条 warn 审计日志，
 * 只记 client_id 的 host 与失败类别，不记响应 body、不记完整文档、不记 token / code。
 *
 * <p>{@link McpCimdClientService} 是条件 Bean（随 {@code yudao.mcp.oauth.enabled} 开关），
 * 而本解析器的调用方（授权 / token Service）是无条件构造的：为避免 enabled=false 时
 * 启动就因缺 Bean 失败，这里用 {@link ObjectProvider} 延迟取 Bean；取不到时 URL 形态
 * 的解析直接按 invalid_client 失败关闭（此时 MCP OAuth 控制器同样是关闭的，正常走不到这里）。
 *
 * @author bujidao
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpOAuthClientResolver {

    private final OAuth2ClientService oauth2ClientService;
    private final ObjectProvider<McpCimdClientService> cimdClientServiceProvider;
    private final McpOAuthProperties properties;

    /**
     * client_id 是否为 CIMD 的 URL 形态：https（allowPrivateAddresses 时放行 http）、
     * 有 host、有非根 path、无 fragment、无 userinfo。判定复用 Block A 的形状校验，
     * 不通过即视为非 URL 形态，走 DB 路径。
     */
    public boolean isCimdClientId(String clientId) {
        if (StrUtil.isBlank(clientId)) {
            return false;
        }
        try {
            McpCimdDocumentValidator.validateClientIdUrl(clientId,
                    McpOAuthProperties.cimdOrDefault(properties).isAllowPrivateAddresses());
            return true;
        } catch (McpCimdException exception) {
            return false;
        }
    }

    /**
     * 按 client_id 形态解析客户端。
     *
     * @param clientId    客户端编号：URL 形态走 CIMD 解析，非 URL 形态走 DB 缓存
     * @param redirectUri 授权场景传本次请求的 redirect_uri（做精确成员校验）；
     *                    token 刷新 / 吊销等不带 redirect_uri 的场景传 null（只拉取并校验文档）
     * @return 可用的客户端；非 URL 形态且 DB 查不到时返回 null；URL 形态失败时抛异常，永不返回 null
     * @throws McpOAuthException URL 形态且 CIMD 拉取 / 校验失败时抛 invalid_client
     */
    public OAuth2ClientDO resolve(String clientId, String redirectUri) {
        if (!isCimdClientId(clientId)) {
            return oauth2ClientService.getOAuth2ClientFromCache(clientId);
        }
        McpCimdClientService cimdClientService = cimdClientServiceProvider.getIfAvailable();
        if (cimdClientService == null) {
            log.warn("[resolve][host({}) error({}) redirectUriMatched({})] CIMD client resolution is not available",
                    hostOf(clientId), "CIMD_DISABLED", null);
            throw McpOAuthException.invalidClient("CIMD client resolution is not available");
        }
        try {
            McpCimdMetadata metadata = redirectUri == null
                    ? cimdClientService.resolveMetadata(clientId)
                    : cimdClientService.resolve(clientId, redirectUri);
            return toClient(metadata);
        } catch (McpCimdException exception) {
            // redirect_uri 是否匹配：只有 INVALID_REDIRECT_URI 能确定为“不匹配”；
            // 其它失败发生在 redirect_uri 校验之前（或根本没传 redirect_uri），记 null 表示“未评估”。
            Boolean redirectUriMatched = redirectUri != null
                    && exception.getError() == McpCimdError.INVALID_REDIRECT_URI ? Boolean.FALSE : null;
            log.warn("[resolve][host({}) error({}) redirectUriMatched({})] CIMD client resolution failed",
                    hostOf(clientId), exception.getError(), redirectUriMatched);
            throw McpOAuthException.invalidClient(
                    "client metadata document is invalid (" + exception.getError().name() + ")");
        }
    }

    /**
     * 把校验通过的 CIMD 文档合成一个临时客户端：不入库、不写缓存。
     *
     * <p>要点：secret 为空（公开客户端）；redirectUris 与 grant_types 直接取文档值；
     * scope 有则解析、无则取 {@code defaultScopes}；additionalInformation 至少含
     * token_endpoint_auth_method=none、public_client=true、require_pkce=true；
     * 不设 mcp_resource，让 {@code McpOAuthResourceValidator} 走 currentResource 回退分支；
     * 有效期取 {@link McpOAuthProperties} 的两个配置值。
     *
     * <p>注意：文档缺省 grant_types 时校验器补的是 {@code [authorization_code]}（Block A 行为），
     * 这里沿用文档值、只在列表为空时才补 {@code [authorization_code, refresh_token]}；
     * 如需缺省即含 refresh_token，应改校验器的缺省值，而不是在这里悄悄放宽显式声明。
     */
    private OAuth2ClientDO toClient(McpCimdMetadata metadata) {
        List<String> grantTypes = metadata.grantTypes();
        if (grantTypes == null || grantTypes.isEmpty()) {
            grantTypes = List.of("authorization_code", "refresh_token");
        }
        List<String> scopes = parseScopes(metadata.scope());
        Map<String, Object> additionalInformation = new LinkedHashMap<>();
        additionalInformation.put("token_endpoint_auth_method", "none");
        additionalInformation.put("public_client", true);
        additionalInformation.put("require_pkce", true);
        additionalInformation.put("cimd_client", true);
        if (StrUtil.isNotBlank(metadata.clientUri())) {
            additionalInformation.put("client_uri", metadata.clientUri());
        }
        if (StrUtil.isNotBlank(metadata.applicationType())) {
            additionalInformation.put("application_type", metadata.applicationType());
        }
        return new OAuth2ClientDO()
                .setClientId(metadata.clientId())
                .setSecret("")
                .setName(metadata.clientName())
                .setLogo(StrUtil.blankToDefault(metadata.logoUri(), properties.getDefaultLogo()))
                .setDescription("Client resolved from client ID metadata document")
                .setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setRedirectUris(List.copyOf(metadata.redirectUris()))
                .setAuthorizedGrantTypes(List.copyOf(grantTypes))
                .setScopes(scopes)
                .setAutoApproveScopes(List.of())
                .setAuthorities(List.of())
                .setResourceIds(List.of())
                .setAdditionalInformation(JsonUtils.toJsonString(additionalInformation))
                .setAccessTokenValiditySeconds(properties.getAccessTokenValiditySeconds())
                .setRefreshTokenValiditySeconds(properties.getRefreshTokenValiditySeconds());
    }

    private List<String> parseScopes(String scope) {
        if (StrUtil.isBlank(scope)) {
            return properties.getDefaultScopes();
        }
        List<String> scopes = OAuth2Utils.buildScopes(scope).stream()
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .distinct()
                .toList();
        return scopes.isEmpty() ? properties.getDefaultScopes() : scopes;
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

}
