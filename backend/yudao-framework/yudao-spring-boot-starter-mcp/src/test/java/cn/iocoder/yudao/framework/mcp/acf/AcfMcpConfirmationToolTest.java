package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.model.CapabilityConfirmationToken;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcfMcpConfirmationToolTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldConfirmChallengeAndReturnToken() {
        CapabilityConfirmationService confirmationService = mock(CapabilityConfirmationService.class);
        ArgumentCaptor<CapabilityContext> contextCaptor = ArgumentCaptor.forClass(CapabilityContext.class);
        when(confirmationService.confirm(org.mockito.ArgumentMatchers.eq("challenge-001"),
                contextCaptor.capture(), org.mockito.ArgumentMatchers.eq("approved")))
                .thenReturn(CapabilityConfirmationToken.builder()
                        .challengeId("challenge-001")
                        .confirmationToken("acf-token-001")
                        .expiresAt(LocalDateTime.of(2026, 7, 30, 16, 0))
                        .build());
        AcfMcpConfirmationTool tool = new AcfMcpConfirmationTool(confirmationService);
        TenantContextHolder.setTenantId(99L);
        McpSchema.CallToolResult result;
        try {
            result = tool.handle(transportContext(), McpSchema.CallToolRequest.builder(AcfMcpConfirmationTool.NAME)
                    .arguments(Map.of("challengeId", "challenge-001", "confirmRemark", "approved"))
                    .build());
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        } finally {
            TenantContextHolder.clear();
        }

        assertThat(result.isError()).isFalse();
        Map<String, Object> structuredContent = (Map<String, Object>) result.structuredContent();
        assertThat(structuredContent).containsEntry("confirmationToken", "acf-token-001")
                .containsEntry("expiresAt", "2026-07-30T16:00");
        assertThat(result.content().get(0).toString()).contains("same idempotencyKey");
        CapabilityContext context = contextCaptor.getValue();
        assertThat(context.getUserId()).isEqualTo(10L);
        assertThat(context.getTenantId()).isEqualTo(20L);
        assertThat(context.getConsumerId()).isEqualTo("user:10");
        assertThat(context.getAttributes()).containsEntry("oauthClientId", "codex");
        verify(confirmationService).confirm(org.mockito.ArgumentMatchers.eq("challenge-001"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("approved"));
    }

    @Test
    void shouldRejectMissingChallengeId() {
        AcfMcpConfirmationTool tool = new AcfMcpConfirmationTool(mock(CapabilityConfirmationService.class));

        McpSchema.CallToolResult result = tool.handle(McpTransportContext.EMPTY,
                McpSchema.CallToolRequest.builder(AcfMcpConfirmationTool.NAME).arguments(Map.of()).build());

        assertThat(result.isError()).isTrue();
        assertThat(result.meta()).containsEntry(AcfMcpToolProtocolMetadata.ERROR_CODE, "BAD_REQUEST");
    }

    private static McpTransportContext transportContext() {
        return McpTransportContext.create(Map.of(
                McpTransportContextKeys.USER_ID, 10L,
                McpTransportContextKeys.TENANT_ID, 20L,
                McpTransportContextKeys.CONSUMER_ID, "user:10",
                McpTransportContextKeys.CLIENT_ID, "codex"));
    }

}
