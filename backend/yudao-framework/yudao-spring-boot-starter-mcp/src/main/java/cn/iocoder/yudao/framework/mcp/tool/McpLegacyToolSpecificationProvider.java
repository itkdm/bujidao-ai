package cn.iocoder.yudao.framework.mcp.tool;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import java.util.List;

/**
 * MCP Tool 规格提供者，仅 legacy stateless server 使用。
 *
 * <p>只服务 legacy 腿（MCP 2025-11-25，{@code McpStatelessSyncServer}）：modern 腿
 * 用另一套 {@code McpSyncToolRepository}，不要把本接口的 provider 接到 modern 腿上。</p>
 *
 * @author bujidao
 */
public interface McpLegacyToolSpecificationProvider {

    /**
     * 创建当前 provider 暴露给 MCP Server 的 Tool 规格。
     *
     * @return MCP Tool 规格列表
     */
    List<McpStatelessServerFeatures.SyncToolSpecification> createToolSpecifications();

}
