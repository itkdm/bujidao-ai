package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolCatalog;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolContractSupport;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AcfMcpToolRepository} 的 modern repository 行为：list 按当前调用身份返回
 * 可见工具（按能力名称升序，与导出顺序无关），find 只按名称查找、不做可见性过滤；
 * 确认不再以合成工具挂在目录里。
 */
class AcfMcpToolRepositoryTest {

    private final CapabilityToolCatalog catalog = mock(CapabilityToolCatalog.class);
    private final AcfMcpToolMapper toolMapper = new AcfMcpToolMapper();
    private final AcfMcpToolCallHandler toolCallHandler = mock(AcfMcpToolCallHandler.class);
    private final CapabilityToolExportService exportService = mock(CapabilityToolExportService.class);

    @Test
    void shouldListOnlyVisibleTools() {
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        CapabilityToolDescriptor hidden = descriptor("demo.hidden", false, false);
        when(catalog.listDeclared()).thenReturn(List.of(first, second, hidden));
        when(exportService.export(ArgumentMatchers.any())).thenReturn(List.of(second));

        ToolsPage page = createRepository().list(requestContext(1L), null);

        assertThat(page.tools()).extracting(McpSchema.Tool::name).containsExactly("demo.second");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldReturnToolsInCapabilityNameOrder() {
        // list 按能力名称升序，与导出顺序无关：导出故意给逆序，两次调用都要排好。
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        when(exportService.export(ArgumentMatchers.any())).thenReturn(List.of(second, first));

        AcfMcpToolRepository repository = createRepository();
        List<String> firstCall = repository.list(requestContext(1L), null).tools().stream()
                .map(McpSchema.Tool::name).toList();
        List<String> secondCall = repository.list(requestContext(1L), null).tools().stream()
                .map(McpSchema.Tool::name).toList();

        assertThat(firstCall).containsExactly("demo.first", "demo.second");
        assertThat(secondCall).containsExactly("demo.first", "demo.second");
    }

    @Test
    void shouldRejectUnknownCursorAsInvalidParams() {
        when(catalog.listDeclared()).thenReturn(List.of());

        assertThatThrownBy(() -> createRepository().list(requestContext(1L), "cursor-1"))
                .isInstanceOf(McpException.class)
                .satisfies(exception -> assertThat(((McpException) exception).error().code())
                        .isEqualTo(-32602));
    }

    @Test
    void shouldFallBackToDeclaredCatalogWhenExportServiceMissing() {
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        when(catalog.listDeclared()).thenReturn(List.of(first, second));

        ToolsPage page = new AcfMcpToolRepository(catalog, toolMapper, toolCallHandler)
                .list(requestContext(1L), null);

        assertThat(page.tools()).extracting(McpSchema.Tool::name)
                .containsExactly("demo.first", "demo.second");
    }

    @Test
    void shouldNotAppendConfirmationTool() {
        CapabilityToolDescriptor descriptor = descriptor("demo.write", true, true);
        when(catalog.listDeclared()).thenReturn(List.of(descriptor));
        when(exportService.export(ArgumentMatchers.any())).thenReturn(List.of(descriptor));

        AcfMcpToolRepository repository = createRepository();

        assertThat(repository.list(requestContext(1L), null).tools())
                .extracting(McpSchema.Tool::name).containsExactly("demo.write");
        assertThat(repository.find(requestContext(1L), "acf.confirm")).isNull();
    }

    @Test
    void shouldReturnNullForUnknownTool() {
        CapabilityToolDescriptor descriptor = descriptor("demo.echo", false, false);
        when(catalog.listDeclared()).thenReturn(List.of(descriptor));

        assertThat(createRepository().find(requestContext(1L), "demo.unknown")).isNull();
        assertThat(createRepository().find(requestContext(1L), null)).isNull();
    }

    @Test
    void shouldNotFilterFindByVisibility() {
        CapabilityToolDescriptor visible = descriptor("demo.visible", false, false);
        CapabilityToolDescriptor hidden = descriptor("demo.hidden", false, false);
        when(catalog.listDeclared()).thenReturn(List.of(visible, hidden));
        when(exportService.export(ArgumentMatchers.any())).thenReturn(List.of(visible));

        McpSyncTool found = createRepository().find(requestContext(1L), "demo.hidden");

        assertThat(found).isNotNull();
        assertThat(found.definition().name()).isEqualTo("demo.hidden");
    }

    @Test
    void shouldKeepFindAndListConsistent() {
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        when(catalog.listDeclared()).thenReturn(List.of(first, second));
        when(exportService.export(ArgumentMatchers.any())).thenReturn(List.of(first, second));

        AcfMcpToolRepository repository = createRepository();
        ToolsPage page = repository.list(requestContext(1L), null);

        assertThat(page.tools()).hasSize(2);
        for (McpSchema.Tool listed : page.tools()) {
            McpSyncTool found = repository.find(requestContext(1L), listed.name());
            assertThat(found).isNotNull();
            assertThat(found.definition()).isEqualTo(listed);
        }
    }

    @Test
    void shouldReturnAllToolsWithoutPagination() {
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        CapabilityToolDescriptor third = descriptor("demo.third", false, false);
        when(exportService.export(ArgumentMatchers.any())).thenReturn(List.of(first, second, third));

        ToolsPage page = createRepository().list(requestContext(1L), null);

        assertThat(page.tools()).extracting(McpSchema.Tool::name)
                .containsExactly("demo.first", "demo.second", "demo.third");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldReflectVisibilityChangeAcrossCallsWithoutCaching() {
        CapabilityToolDescriptor first = descriptor("demo.first", false, false);
        CapabilityToolDescriptor second = descriptor("demo.second", false, false);
        when(exportService.export(ArgumentMatchers.any())).thenAnswer(invocation -> {
            CapabilityVisibilityQuery query = invocation.getArgument(0);
            Long userId = query.getContext() == null ? null : query.getContext().getUserId();
            return Long.valueOf(2L).equals(userId) ? List.of(first, second) : List.of(first);
        });

        AcfMcpToolRepository repository = createRepository();

        assertThat(repository.list(requestContext(1L), null).tools())
                .extracting(McpSchema.Tool::name).containsExactly("demo.first");
        assertThat(repository.list(requestContext(2L), null).tools())
                .extracting(McpSchema.Tool::name).containsExactly("demo.first", "demo.second");
        // 换回用户 1：结果跟着变，证明 list 没有拿上一轮的缓存充数。
        assertThat(repository.list(requestContext(1L), null).tools())
                .extracting(McpSchema.Tool::name).containsExactly("demo.first");
    }

    @Test
    void shouldReturnEmptyListWhenNoToolsDeclared() {
        when(catalog.listDeclared()).thenReturn(List.of());

        ToolsPage page = new AcfMcpToolRepository(catalog, toolMapper, toolCallHandler)
                .list(requestContext(1L), null);

        assertThat(page.tools()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    private AcfMcpToolRepository createRepository() {
        return new AcfMcpToolRepository(catalog, toolMapper, toolCallHandler, exportService);
    }

    private static McpRequestContext requestContext(Long userId) {
        McpRequestContext context = mock(McpRequestContext.class);
        when(context.transportContext()).thenReturn(McpTransportContext.create(Map.of(
                McpTransportContextKeys.USER_ID, userId,
                McpTransportContextKeys.TENANT_ID, 2L,
                McpTransportContextKeys.CONSUMER_ID, "user:" + userId)));
        return context;
    }

    private static CapabilityToolDescriptor descriptor(String name, boolean sideEffect,
                                                       boolean confirmationRequired) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn(name);
        when(descriptor.getTitle()).thenReturn("Demo Tool");
        when(descriptor.getDescription()).thenReturn("Demo description");
        when(descriptor.getVersion()).thenReturn("1.0.0");
        when(descriptor.getRiskLevel()).thenReturn(CapabilityRiskLevel.LOW);
        when(descriptor.getInputSchema()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(descriptor.getOutputSchema()).thenReturn(Map.of("type", "string"));
        when(descriptor.isSideEffect()).thenReturn(sideEffect);
        when(descriptor.isConfirmationRequired()).thenReturn(confirmationRequired);
        when(descriptor.isIdempotencyRequired()).thenReturn(sideEffect || confirmationRequired);
        return descriptor;
    }

}
