package cn.iocoder.yudao.framework.mcp.acf;

import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把多个 {@link McpSyncToolRepository} 合成一个。
 *
 * <p>list：按仓库顺序合并去重（重名工具以后面的为准，位置保留第一次出现处）；
 * find：按仓库顺序查找，第一个返回非 null 的胜出。空列表时即为空仓库：
 * list 返回空页、find 返回 null。</p>
 *
 * @author bujidao
 */
public class CompositeMcpSyncToolRepository implements McpSyncToolRepository {

    private final List<McpSyncToolRepository> repositories;

    public CompositeMcpSyncToolRepository(List<McpSyncToolRepository> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    @Override
    public ToolsPage list(McpRequestContext ctx, String cursor) {
        Map<String, io.modelcontextprotocol.modern.McpSchema.Tool> tools = new LinkedHashMap<>();
        for (McpSyncToolRepository repository : repositories) {
            for (io.modelcontextprotocol.modern.McpSchema.Tool tool : repository.list(ctx, cursor).tools()) {
                tools.put(tool.name(), tool);
            }
        }
        return ToolsPage.of(new ArrayList<>(tools.values()));
    }

    @Override
    public McpSyncTool find(McpRequestContext ctx, String name) {
        for (McpSyncToolRepository repository : repositories) {
            McpSyncTool tool = repository.find(ctx, name);
            if (tool != null) {
                return tool;
            }
        }
        return null;
    }

}
