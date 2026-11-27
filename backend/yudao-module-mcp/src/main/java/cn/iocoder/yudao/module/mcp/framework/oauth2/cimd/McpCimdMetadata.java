package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import java.util.List;

/**
 * CIMD 文档解析后的不可变值对象。
 *
 * <p>字段对应 draft-ietf-oauth-client-id-metadata-document 的客户端元数据字段。
 * 其中 {@code jwks} 保留原始 JSON 文本（文档里它通常是 JSON 对象，序列化后存放）；
 * {@code grantTypes} / {@code responseTypes} 缺省时分别补默认值
 * {@code [authorization_code, refresh_token]} / {@code [code]}，由校验器负责填充。
 *
 * @author bujidao
 */
public record McpCimdMetadata(

        /** 拉取文档用的 client_id URL，与文档内 client_id 逐字节相等。 */
        String clientId,
        /** 客户端展示名，非空；缺失/空白时取 client_id URL 的 host。 */
        String clientName,
        /** 注册的回调地址列表，非空。 */
        List<String> redirectUris,
        /** 允许的授权类型，缺省 [authorization_code, refresh_token]。 */
        List<String> grantTypes,
        /** 允许的响应类型，缺省 [code]。 */
        List<String> responseTypes,
        /** token 端点认证方式，只允许 none，缺省 none。private_key_jwt 待后续实现验签后再放开。 */
        String tokenEndpointAuthMethod,
        /** JWKS 地址（现阶段只做形状校验、不用于验签）。 */
        String jwksUri,
        /** JWKS 原始 JSON，可选。 */
        String jwks,
        /** 客户端主页，可选。 */
        String clientUri,
        /** 客户端 logo，可选。 */
        String logoUri,
        /** 申请的 scope，空格分隔，可选。 */
        String scope,
        /** 应用类型，缺省 web。 */
        String applicationType

) {

    public McpCimdMetadata {
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        grantTypes = grantTypes == null ? List.of() : List.copyOf(grantTypes);
        responseTypes = responseTypes == null ? List.of() : List.copyOf(responseTypes);
    }

}
