package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * CIMD 对外唯一入口：给定 client_id URL 与授权请求里的 redirect_uri，
 * 返回校验通过的客户端元数据。
 *
 * <p>流程：校验 URL 形状 → 查缓存（命中也要校验本次请求的 redirect_uri，通过才返回）→
 * 拉取 → 校验文档 → 校验请求 redirect_uri 在文档 redirect_uris 里（精确相等）且符合 scheme 规则 →
 * 入缓存 → 返回。任一步失败抛 {@link McpCimdException}，不吞掉、不返回 null。
 *
 * @author bujidao
 */
@Service
@ConditionalOnProperty(prefix = McpOAuthProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class McpCimdClientService {

    private final McpOAuthProperties properties;
    private final McpCimdFetcher fetcher;
    private final McpCimdMetadataCache cache;

    public McpCimdClientService(McpOAuthProperties properties) {
        this(properties,
                new McpCimdFetcher(McpOAuthProperties.cimdOrDefault(properties), JsonUtils.getObjectMapper()),
                new McpCimdMetadataCache(
                        McpOAuthProperties.cimdOrDefault(properties).getCacheMaxAge(),
                        McpOAuthProperties.cimdOrDefault(properties).getCacheDefaultTtl(),
                        McpOAuthProperties.cimdOrDefault(properties).getCacheMaxEntries()));
    }

    @Autowired
    public McpCimdClientService(McpOAuthProperties properties, McpCimdFetcher fetcher,
                               McpCimdMetadataCache cache) {
        if (properties == null) {
            // properties 为 null 时 cimdOrDefault 能兜底 cimd，但 resolve() 里
            // validateDocument / validateRequestedRedirectUri 直接调
            // properties.getLocalRedirectHosts() 会 NPE：明确不支持 null，
            // 早失败而不是在某条分支上才暴露出来
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
        this.fetcher = fetcher;
        this.cache = cache;
    }

    /**
     * 解析并校验 CIMD 客户端。
     *
     * @param clientIdUrl 客户端提供的 client_id URL
     * @param redirectUri 授权请求里传来的 redirect_uri
     * @return 校验通过的客户端元数据
     * @throws McpCimdException 任一步失败
     */
    public McpCimdMetadata resolve(String clientIdUrl, String redirectUri) throws McpCimdException {
        McpCimdMetadata metadata = resolveMetadata(clientIdUrl);
        // 校验请求 redirect_uri：在文档列表里（精确相等）且符合 scheme 规则
        validateRequestedRedirectUri(metadata, redirectUri);
        return metadata;
    }

    /**
     * 拉取并校验 CIMD 文档，但不校验本次请求的 redirect_uri。
     *
     * <p>供 token 刷新 / 吊销等不带 redirect_uri 的场景使用：只证明“文档合法”，
     * 不证明“某个回调地址在文档里”。调用方不得把这里返回的元数据用于放行回调地址。
     *
     * @param clientIdUrl 客户端提供的 client_id URL
     * @return 校验通过的客户端元数据
     * @throws McpCimdException 任一步失败
     */
    public McpCimdMetadata resolveMetadata(String clientIdUrl) throws McpCimdException {
        McpOAuthProperties.Cimd cimd = McpOAuthProperties.cimdOrDefault(properties);
        // 1. 校验 URL 形状（allowPrivateAddresses=true 时放行 http，仅本地开发 / 测试用）
        McpCimdDocumentValidator.validateClientIdUrl(clientIdUrl, cimd.isAllowPrivateAddresses());
        // 2. 查缓存：命中直接返回。本方法不校验 redirect_uri，所以返回的元数据
        //    不能用于放行回调地址；需要校验回调的调用方必须走 resolve()，
        //    它在缓存命中时也会校验本次请求的 redirect_uri。
        McpCimdMetadata cached = cache.get(clientIdUrl);
        if (cached != null) {
            return cached;
        }
        // 3. 拉取
        McpCimdFetcher.FetchedDocument fetched = fetcher.fetch(clientIdUrl);
        // 4. 校验文档
        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                clientIdUrl, fetched.document(), properties.getLocalRedirectHosts());
        // 5. 入缓存（失败 / 无效文档走不到这里，永不入缓存）
        cache.put(clientIdUrl, metadata, fetched.maxAgeSeconds());
        return metadata;
    }

    /** 校验本次请求的 redirect_uri：在文档列表里（精确相等）且符合 scheme 规则。 */
    private void validateRequestedRedirectUri(McpCimdMetadata metadata, String redirectUri)
            throws McpCimdException {
        if (!metadata.redirectUris().contains(redirectUri)) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI,
                    "redirect_uri is not registered in the client metadata document");
        }
        McpCimdDocumentValidator.validateRedirectUri(redirectUri, properties.getLocalRedirectHosts());
    }

}
