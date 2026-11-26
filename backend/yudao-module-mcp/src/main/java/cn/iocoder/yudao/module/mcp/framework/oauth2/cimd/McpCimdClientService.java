package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import org.springframework.stereotype.Service;

/**
 * CIMD 对外唯一入口：给定 client_id URL 与授权请求里的 redirect_uri，
 * 返回校验通过的客户端元数据。
 *
 * <p>流程：校验 URL 形状 → 查缓存（命中且未过期直接返回）→ 拉取 → 校验文档 →
 * 校验请求 redirect_uri 在文档 redirect_uris 里（精确相等）且符合 scheme 规则 →
 * 入缓存 → 返回。任一步失败抛 {@link McpCimdException}，不吞掉、不返回 null。
 *
 * @author bujidao
 */
@Service
public class McpCimdClientService {

    private final McpOAuthProperties properties;
    private final McpCimdFetcher fetcher;
    private final McpCimdMetadataCache cache;

    public McpCimdClientService(McpOAuthProperties properties) {
        this(properties,
                new McpCimdFetcher(properties.getCimd(), JsonUtils.getObjectMapper()),
                new McpCimdMetadataCache(properties.getCimd().getCacheMaxAge(),
                        properties.getCimd().getCacheDefaultTtl(), properties.getCimd().getCacheMaxEntries()));
    }

    McpCimdClientService(McpOAuthProperties properties, McpCimdFetcher fetcher, McpCimdMetadataCache cache) {
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
        McpOAuthProperties.Cimd cimd = properties.getCimd();
        // 1. 校验 URL 形状（allowPrivateAddresses=true 时放行 http，仅本地开发 / 测试用）
        McpCimdDocumentValidator.validateClientIdUrl(clientIdUrl, cimd.isAllowPrivateAddresses());
        // 2. 查缓存
        McpCimdMetadata cached = cache.get(clientIdUrl);
        if (cached != null) {
            return cached;
        }
        // 3. 拉取
        McpCimdFetcher.FetchedDocument fetched = fetcher.fetch(clientIdUrl);
        // 4. 校验文档
        McpCimdMetadata metadata = McpCimdDocumentValidator.validateDocument(
                clientIdUrl, fetched.document(), properties.getLocalRedirectHosts());
        // 5. 校验请求 redirect_uri：在文档列表里（精确相等）且符合 scheme 规则
        if (!metadata.redirectUris().contains(redirectUri)) {
            throw new McpCimdException(McpCimdError.INVALID_REDIRECT_URI,
                    "redirect_uri is not registered in the client metadata document");
        }
        McpCimdDocumentValidator.validateRedirectUri(redirectUri, properties.getLocalRedirectHosts());
        // 6. 入缓存（失败 / 无效文档走不到这里，永不入缓存）
        cache.put(clientIdUrl, metadata, fetched.maxAgeSeconds());
        return metadata;
    }

}
