package cn.iocoder.yudao.framework.mcp;

import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolDescriptor;
import cn.iocoder.yudao.framework.mcp.config.YudaoMcpAutoConfiguration;
import cn.iocoder.yudao.framework.mcp.transport.McpProtocolDispatcherServlet;
import cn.iocoder.yudao.framework.web.core.filter.CacheRequestBodyWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 测试共享 helper：此前 dispatch() / cachedJsonRequest() / toolNames() /
 * descriptor() 在四个测试文件里各造一套，本类收拢完全相同的实现，各文件改用它。
 *
 * <p>未收拢的重复（有意保留）：{@code legacyRequest()} 只在
 * {@code McpProtocolDispatcherEndToEndTest} 出现一次；{@code descriptor(Map, Map)}
 *（{@code AcfMcpToolCallHandlerTest}）与 {@code descriptor(String, boolean, boolean)}
 *（{@code AcfMcpToolRepositoryTest}）签名与桩内容各不相同，硬合并反而难读。</p>
 */
public final class McpTestFixtures {

    private McpTestFixtures() {
    }

    /**
     * 直接调注册在 /mcp 上的分流器（GenericServlet 的 public service 重载，
     * protected 的 HttpServlet 重载在 config / transport 包调不到）。
     */
    public static MockHttpServletResponse dispatch(
            ApplicationContext context, HttpServletRequest request) throws Exception {
        McpProtocolDispatcherServlet dispatcher = (McpProtocolDispatcherServlet) context.getBean(
                        YudaoMcpAutoConfiguration.MCP_SERVLET_NAME, ServletRegistrationBean.class)
                .getServlet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        dispatcher.service((ServletRequest) request, (ServletResponse) response);
        return response;
    }

    /**
     * 包好 body 缓存的 JSON POST 请求（requestURI 固定 /mcp）：分流器只在请求
     * 被包成 {@code CacheRequestBodyWrapper} 时读体。
     */
    public static HttpServletRequest cachedJsonRequest(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/mcp");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return new CacheRequestBodyWrapper(request);
    }

    /**
     * 老客户端的请求头（供 TestRestTemplate 用）：只有 Content-Type 与 Accept，
     * 没有任何 modern 头（无 Mcp-Method、无 MCP-Protocol-Version）。Accept
     * 必须同时含 application/json 与 text/event-stream，否则 legacy
     * transport 以 400 拒绝。
     */
    public static HttpHeaders legacyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        return headers;
    }

    /** Modern 客户端的请求头（供 TestRestTemplate 用）：见 {@link #modernHeaders(String, String)}。 */
    public static HttpHeaders modernHeaders(String method) {
        return modernHeaders(method, null);
    }

    /**
     * Modern 客户端的请求头（供 TestRestTemplate 用）：Content-Type 与 Accept
     * 同 legacy，另带 {@code Mcp-Method}、{@code MCP-Protocol-Version:
     * 2026-07-28}；{@code tools/call} / {@code prompts/get} 等按名调用的
     * 方法还要带 {@code Mcp-Name}。
     */
    public static HttpHeaders modernHeaders(String method, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("Mcp-Method", method);
        headers.set("MCP-Protocol-Version", "2026-07-28");
        if (name != null) {
            headers.set("Mcp-Name", name);
        }
        return headers;
    }

    /** Modern 客户端的 servlet 请求（供分流器单测用）：见 {@link #modernRequest(String, String, String)}。 */
    public static HttpServletRequest modernRequest(String json, String method) {
        return modernRequest(json, method, null);
    }

    /**
     * Modern 客户端的 servlet 请求（供分流器单测用）：POST /mcp、包好 body
     * 缓存，头与 {@link #modernHeaders(String, String)} 一致。
     */
    public static HttpServletRequest modernRequest(String json, String method, String name) {
        MockHttpServletRequest source = new MockHttpServletRequest();
        source.setMethod("POST");
        source.setRequestURI("/mcp");
        source.setContentType("application/json");
        source.addHeader("Accept", "application/json, text/event-stream");
        source.setContent(json.getBytes(StandardCharsets.UTF_8));
        source.addHeader("Mcp-Method", method);
        source.addHeader("MCP-Protocol-Version", "2026-07-28");
        if (name != null) {
            source.addHeader("Mcp-Name", name);
        }
        return new CacheRequestBodyWrapper(source);
    }

    /** 从 tools/list 响应里取出工具名列表（按响应顺序）。 */
    public static List<String> toolNames(JsonNode payload) {
        List<String> names = new ArrayList<>();
        payload.path("result").path("tools").forEach(tool -> names.add(tool.path("name").asText()));
        return names;
    }

    /** 不需确认的 ERP 能力描述（mock）：与 {@link #descriptor(String, boolean)} 同形。 */
    public static CapabilityToolDescriptor descriptor(String name) {
        return descriptor(name, false);
    }

    /** ERP 能力描述（mock）：message 入参、string 出参，确认需求由参数指定。 */
    public static CapabilityToolDescriptor descriptor(String name, boolean confirmationRequired) {
        CapabilityToolDescriptor descriptor = mock(CapabilityToolDescriptor.class);
        when(descriptor.getCapabilityName()).thenReturn(name);
        when(descriptor.getTitle()).thenReturn(name);
        when(descriptor.getDescription()).thenReturn("ERP capability " + name);
        when(descriptor.getInputSchema()).thenReturn(Map.of("type", "object", "properties",
                Map.of("message", Map.of("type", "string"))));
        when(descriptor.getOutputSchema()).thenReturn(Map.of("type", "string"));
        when(descriptor.isConfirmationRequired()).thenReturn(confirmationRequired);
        return descriptor;
    }

}
