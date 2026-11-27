package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityDefinition;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityVisibilityService;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcfMcpLegacyToolsListFilterTest {

    private final CapabilityVisibilityService visibilityService =
            mock(CapabilityVisibilityService.class);
    private final AcfMcpLegacyToolsListFilter filter =
            new AcfMcpLegacyToolsListFilter(visibilityService);

    private static final McpTransportContext CONTEXT = McpTransportContext.create(Map.of(
            McpTransportContextKeys.USER_ID, 100L,
            McpTransportContextKeys.TENANT_ID, 1L,
            McpTransportContextKeys.CONSUMER_ID, "user:100"));

    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder(name, Map.of("type", "object")).build();
    }

    @Test
    void shouldShowVisibleTool() {
        when(visibilityService.getVisible(eq("cap-a"), any()))
                .thenReturn(Optional.of(mock(CapabilityDefinition.class)));

        assertThat(filter.isVisible(CONTEXT, tool("cap-a"))).isTrue();
    }

    @Test
    void shouldHideInvisibleTool() {
        when(visibilityService.getVisible(eq("cap-b"), any()))
                .thenReturn(Optional.empty());

        assertThat(filter.isVisible(CONTEXT, tool("cap-b"))).isFalse();
    }

    @Test
    void shouldPassCallerIdentityFromTransportContext() {
        when(visibilityService.getVisible(eq("cap-a"), any()))
                .thenReturn(Optional.of(mock(CapabilityDefinition.class)));

        filter.isVisible(CONTEXT, tool("cap-a"));

        ArgumentCaptor<CapabilityVisibilityQuery> queryCaptor =
                ArgumentCaptor.forClass(CapabilityVisibilityQuery.class);
        verify(visibilityService).getVisible(eq("cap-a"), queryCaptor.capture());
        CapabilityContext context = queryCaptor.getValue().getContext();
        assertThat(context.getUserId()).isEqualTo(100L);
        assertThat(context.getTenantId()).isEqualTo(1L);
        assertThat(context.getConsumerId()).isEqualTo("user:100");
    }

    @Test
    void shouldHideNullTool() {
        assertThat(filter.isVisible(CONTEXT, null)).isFalse();
    }

    @Test
    void shouldShowAllToolsWhenVisibilityServiceIsAbsent() {
        // 与 modern 仓库缺可见性服务时退回声明目录一致：不过滤。
        AcfMcpLegacyToolsListFilter passThroughFilter = new AcfMcpLegacyToolsListFilter(null);

        assertThat(passThroughFilter.isVisible(CONTEXT, tool("cap-a"))).isTrue();
    }

}
