package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.service.CapabilityConfirmationService;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcfMcpToolsListFilterTest {

    private final CapabilityToolExportService toolExportService = mock(CapabilityToolExportService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AcfMcpToolsListFilter filter = new AcfMcpToolsListFilter(
            toolExportService, new AcfMcpToolMapper(), objectMapper);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnOnlyVisibleToolsForCurrentLoginUser() throws ServletException, IOException {
        LoginUser loginUser = new LoginUser()
                .setId(1001L)
                .setTenantId(2001L)
                .setVisitTenantId(3001L);
        loginUser.setContext(McpTransportContextKeys.CLIENT_ID, "codex");
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        CapabilityToolDescriptor productSearch = descriptor("erp.product.search");
        when(toolExportService.export(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(productSearch));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json;charset=UTF-8");
        request.addHeader("Accept", "application/json");
        request.setContent("""
                {"jsonrpc":"2.0","id":7,"method":"tools/list","params":{}}
                """.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode payload = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(payload.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(payload.path("id").asInt()).isEqualTo(7);
        assertThat(payload.path("result").path("tools")).hasSize(1);
        assertThat(payload.path("result").path("tools").get(0).path("name").asText())
                .isEqualTo("erp.product.search");

        ArgumentCaptor<CapabilityVisibilityQuery> captor = ArgumentCaptor.forClass(CapabilityVisibilityQuery.class);
        verify(toolExportService).export(captor.capture());
        assertThat(captor.getValue().getContext().getUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getContext().getTenantId()).isEqualTo(3001L);
        assertThat(captor.getValue().getContext().getConsumerId()).isEqualTo("user:1001");
        assertThat(captor.getValue().getContext().getAttributes()).containsEntry("oauthClientId", "codex");
    }

    @Test
    void shouldPassThroughNonListRequestsWithBodyPreserved() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json;charset=UTF-8");
        request.setContent("""
                {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"erp.product.search"}}
                """.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = (downstreamRequest, downstreamResponse) -> {
            String body = downstreamRequest.getReader().readLine();
            assertThat(body).contains("\"method\":\"tools/call\"");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAppendConfirmationToolWhenConfigured() throws ServletException, IOException {
        AcfMcpToolsListFilter filter = new AcfMcpToolsListFilter(
                toolExportService, new AcfMcpToolMapper(), objectMapper,
                new AcfMcpConfirmationTool(mock(CapabilityConfirmationService.class)));
        CapabilityToolDescriptor descriptor = descriptor("erp.product.search");
        when(toolExportService.export(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(descriptor));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json;charset=UTF-8");
        request.addHeader("Accept", "application/json");
        request.setContent("""
                {"jsonrpc":"2.0","id":9,"method":"tools/list","params":{}}
                """.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode tools = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8))
                .path("result").path("tools");
        assertThat(tools).hasSize(2);
        assertThat(tools.get(1).path("name").asText()).isEqualTo(AcfMcpConfirmationTool.NAME);
    }

    private static CapabilityToolDescriptor descriptor(String name) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn(name);
        when(descriptor.getTitle()).thenReturn("Product Search");
        when(descriptor.getDescription()).thenReturn("Search ERP products");
        when(descriptor.getVersion()).thenReturn("1.0.0");
        when(descriptor.getRiskLevel()).thenReturn(CapabilityRiskLevel.LOW);
        when(descriptor.getInputSchema()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(descriptor.getOutputSchema()).thenReturn(Map.of("type", "object"));
        when(descriptor.isSideEffect()).thenReturn(false);
        when(descriptor.isConfirmationRequired()).thenReturn(false);
        when(descriptor.isIdempotencyRequired()).thenReturn(false);
        return descriptor;
    }

}
