package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

/**
 * CIMD（Client ID Metadata Document，见 MCP 规范 OAuth 2.1 + draft-ietf-oauth-client-id-metadata-document）
 * 处理过程中的错误类别。
 *
 * <p>异常信息只写原因类别与安全的位置信息（URL、状态码），绝不包含拉取到的响应 body 原文，
 * 因为 body 是远端攻击者可控文本，可能夹带日志注入或敏感信息。
 *
 * @author bujidao
 */
public enum McpCimdError {

    /** client_id URL 形状不合法。 */
    INVALID_CLIENT_ID_URL,
    /** 解析出的地址是回环 / 内网 / 链路本地 / 组播 / 未指定地址，SSRF 防护拦截。 */
    SSRF_BLOCKED,
    /** 拉取失败：DNS 解析失败、连接失败、IO 异常等。 */
    FETCH_FAILED,
    /** 拉取超时。 */
    FETCH_TIMEOUT,
    /** HTTP 状态码不是 200。 */
    INVALID_STATUS,
    /** Content-Type 不是 application/json。 */
    INVALID_CONTENT_TYPE,
    /** 响应 body 超过大小上限。 */
    DOCUMENT_TOO_LARGE,
    /** 文档内容校验失败：JSON 解析失败、字段缺失或取值不合法。 */
    INVALID_DOCUMENT,
    /** 授权请求里的 redirect_uri 不合法：不在文档列表中，或 scheme 规则不通过。 */
    INVALID_REDIRECT_URI

}
