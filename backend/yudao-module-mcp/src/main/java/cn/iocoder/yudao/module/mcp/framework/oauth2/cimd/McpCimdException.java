package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

/**
 * CIMD 处理专用受检异常。
 *
 * <p>使用受检异常，强制调用方（后续 Block 的授权端点）显式处理拉取 / 校验失败，
 * 不能悄悄吞掉失败继续用未校验的客户端信息。异常信息只含 {@link McpCimdError} 类别
 * 与安全的位置信息，绝不含响应 body 原文。
 *
 * @author bujidao
 */
public class McpCimdException extends Exception {

    private final McpCimdError error;

    public McpCimdException(McpCimdError error, String detail) {
        super(detail == null || detail.isBlank() ? error.name() : error.name() + ": " + detail);
        this.error = error;
    }

    public McpCimdException(McpCimdError error) {
        this(error, null);
    }

    public McpCimdException(McpCimdError error, String detail, Throwable cause) {
        super(detail == null || detail.isBlank() ? error.name() : error.name() + ": " + detail, cause);
        this.error = error;
    }

    public McpCimdError getError() {
        return error;
    }

}
