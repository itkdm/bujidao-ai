package cn.iocoder.yudao.framework.mcp.tool;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import java.util.List;

/**
 * MCP Tool 规格提供者。
 *
 * @author bujidao
 */
public interface McpToolSpecificationProvider {

    /**
     * 创建当前 provider 暴露给 MCP Server 的 Tool 规格。
     *
     * @return MCP Tool 规格列表
     */
    List<McpStatelessServerFeatures.SyncToolSpecification> createToolSpecifications();

}
