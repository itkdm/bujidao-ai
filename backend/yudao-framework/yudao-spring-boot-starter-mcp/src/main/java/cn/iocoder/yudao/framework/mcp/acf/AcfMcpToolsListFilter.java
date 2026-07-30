package cn.iocoder.yudao.framework.mcp.acf;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityConsumerType;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityContext;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityVisibilityQuery;
import cn.iocoder.yudao.framework.acf.core.tool.CapabilityToolExportService;
import cn.iocoder.yudao.framework.mcp.security.McpTransportContextKeys;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 按当前 MCP 调用身份动态返回 tools/list。
 *
 * <p>MCP Java SDK 2.0.0 的 stateless server 只维护静态工具列表。ACF 需要 discover-time
 * authorization，因此这里在 MCP 安全链完成认证和租户上下文绑定后拦截 tools/list，
 * 只返回当前用户可见的能力工具；tools/call 仍由 SDK 路由并进入 ACF 执行阶段二次校验。</p>
 *
 * @author bujidao
 */
public class AcfMcpToolsListFilter extends OncePerRequestFilter {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String SOURCE = "MCP";
    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String ACCEPT = "Accept";

    private final CapabilityToolExportService toolExportService;
    private final AcfMcpToolMapper toolMapper;
    private final ObjectMapper objectMapper;

    public AcfMcpToolsListFilter(CapabilityToolExportService toolExportService,
                                 AcfMcpToolMapper toolMapper,
                                 ObjectMapper objectMapper) {
        this.toolExportService = toolExportService;
        this.toolMapper = toolMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request, body);
        JsonNode jsonRpcRequest = readJsonObject(body);
        if (jsonRpcRequest == null || !McpSchema.METHOD_TOOLS_LIST.equals(text(jsonRpcRequest.get("method")))) {
            filterChain.doFilter(cachedRequest, response);
            return;
        }
        Object id = objectMapper.convertValue(jsonRpcRequest.get("id"), Object.class);
        McpSchema.ListToolsResult result = new McpSchema.ListToolsResult(listVisibleTools(), null);
        writeJsonRpcResponse(request, response, McpSchema.JSONRPCResponse.result(id, result));
    }

    private JsonNode readJsonObject(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node != null && node.isObject() ? node : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private List<McpSchema.Tool> listVisibleTools() {
        CapabilityVisibilityQuery query = CapabilityVisibilityQuery.builder()
                .context(createContext())
                .build();
        return toolExportService.export(query).stream()
                .map(toolMapper::toTool)
                .toList();
    }

    private static CapabilityContext createContext() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            return CapabilityContext.empty();
        }
        Long tenantId = loginUser.getVisitTenantId() != null
                ? loginUser.getVisitTenantId() : loginUser.getTenantId();
        String clientId = loginUser.getContext(McpTransportContextKeys.CLIENT_ID, String.class);
        return CapabilityContext.builder()
                .userId(loginUser.getId())
                .tenantId(tenantId)
                .source(SOURCE)
                .consumerType(CapabilityConsumerType.MCP)
                .consumerId("user:" + loginUser.getId())
                .attributes(clientId == null ? Map.of() : Map.of("oauthClientId", clientId))
                .build();
    }

    private void writeJsonRpcResponse(HttpServletRequest request, HttpServletResponse response,
                                      McpSchema.JSONRPCResponse jsonRpcResponse) throws IOException {
        String body = objectMapper.writeValueAsString(jsonRpcResponse);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (acceptsEventStream(request)) {
            response.setContentType(TEXT_EVENT_STREAM + ";charset=UTF-8");
            response.getWriter().write("data: ");
            response.getWriter().write(body);
            response.getWriter().write("\n\n");
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(body);
        }
    }

    private static boolean acceptsEventStream(HttpServletRequest request) {
        String accept = request.getHeader(ACCEPT);
        return accept != null && accept.contains(TEXT_EVENT_STREAM);
    }

    private static String text(JsonNode node) {
        return node == null || !node.isTextual() ? null : node.asText();
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Async read listener is not supported");
                }

                @Override
                public int read() {
                    return inputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

}
