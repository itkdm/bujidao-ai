package cn.iocoder.yudao.framework.mcp.transport;

import cn.iocoder.yudao.framework.web.core.filter.CacheRequestBodyWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.modern.McpHttp.Headers;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * /mcp 的双协议分流入口：同一 endpoint 同时服务 MCP 2026-07-28（modern）与
 * 2025-11-25（legacy stateless）。
 *
 * <p>只做分流，不复制认证逻辑、不碰安全链：认证、scope、租户、限流、字符集等过滤器
 * 都在分流器之前跑完，两条腿共用同一套。分流器拿到请求后直接调目标 transport 的
 * {@code service(req, resp)} 转发——两个 transport 都是 {@code HttpServlet}
 *（SDK v2.2.0 tag 源码：{@code HttpServletMcpTransport} 与
 * {@code HttpServletStatelessServerTransport} 均 extends HttpServlet，且都不重写
 * {@code init()}，无需转发 servlet 生命周期；两者的 destroy 仍由 Spring 按 bean
 * 的 destroyMethod 调用）。注意 {@code HttpServlet.service(HttpServletRequest,
 * HttpServletResponse)} 是 protected、兄弟子类调不到，这里调的是
 * {@code GenericServlet} 的 public 重载
 * {@code service(ServletRequest, ServletResponse)}（需显式转型以命中该重载），
 * 它内部再分派到 protected 的 HttpServlet 重载，行为完全一致。</p>
 *
 * <p>判据顺序（先 HTTP 方法、再头、再体；命中即分流）：GET / DELETE 一律 legacy；
 * POST 带 {@code Mcp-Method} 头的一律 modern；{@code MCP-Protocol-Version} 为
 * 2026-07-28 的 modern、其它值 legacy（仅凭该头存在不能分流，2025-11-25 也要求
 * 该头，判的是值）；请求体 {@code _meta} 的协议版本为 2026-07-28 的 modern；
 * 方法为 {@code server/discover} 的 modern；方法为 {@code initialize}（且无
 * modern 信号）的 legacy；其余 POST 默认 legacy（老客户端兼容优先）。</p>
 *
 * <p>读体只走已有的 body 缓存：全局 {@code CacheRequestBodyFilter}（/*，JSON 请求）
 * 已把请求包成 {@code CacheRequestBodyWrapper}，其 {@code getInputStream()} 每次
 * 返回新的流，可反复读；分流器沿包装链找到它才读体，找不到（非 JSON、或 web starter
 * 未启用）时只按头判定、绝不消耗原始输入流——modern 客户端一定带
 * {@code Mcp-Method} 头，所以头判定已足够把 modern 流量认出来，体规则只是兜底。
 * 分流后把同一个（缓存过的）请求交给 transport，transport 还能再读到完整 body。</p>
 *
 * <p>边界：未知 HTTP 方法、未知 JSON-RPC 方法、无头 POST、重复 initialize、
 * {@code Mcp-Session-Id} 头——前三者都落到默认 legacy（modern 腿不建 session，
 * session 头不是分流信号，目标服务端自己决定怎么答）；重复 initialize 由目标
 * 服务端按自己的无状态语义处理，分流器不拦截。</p>
 *
 * <p>Legacy 开关（{@code yudao.mcp.server.legacy.enabled}，默认 true）：关掉后
 * 分流到 legacy 的请求在这里被明确拒绝，不转发给任何一条 transport——既不静默
 * 落到 modern，也不产生 500。拒绝形态：JSON-RPC 请求（带 {@code id}）答
 * HTTP 400 + JSON-RPC 错误信封（{@code code -32022}，与 modern transport 对不
 * 支持协议版本的回答一致，见 SDK v2.2.0
 * {@code HttpServletMcpTransport.DEFAULT_ERROR_STATUSES}）；通知（无 {@code id}）
 * 按 JSON-RPC 语义无响应，答 HTTP 202；无 body 的请求（GET / DELETE 等）答
 * HTTP 400 + {@code id: null} 的错误信封。Modern 请求不受开关影响。
 * 拒绝的那一行分流日志记为 {@code rule=legacy-disabled}，便于以后按流量决定何时
 * 下掉 legacy。</p>
 *
 * @author bujidao
 */
public class McpProtocolDispatcherServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(McpProtocolDispatcherServlet.class);

    /** Modern 协议版本：SDK v2.2.0 的 {@code McpSchema.LATEST_PROTOCOL_VERSION}。 */
    static final String MODERN_PROTOCOL_VERSION = McpSchema.LATEST_PROTOCOL_VERSION;

    /** 分流目标。 */
    public enum Leg {
        MODERN,
        LEGACY
    }

    /** 一次分流的结果：目标腿、协商到的协议版本（认不出来时为 "-"）、命中的规则名。 */
    public record Decision(Leg leg, String protocolVersion, String rule) {
    }

    private final HttpServletMcpTransport modernTransport;
    private final HttpServletStatelessServerTransport legacyTransport;
    private final ObjectMapper objectMapper;
    private final boolean legacyEnabled;

    public McpProtocolDispatcherServlet(HttpServletMcpTransport modernTransport,
                                        HttpServletStatelessServerTransport legacyTransport,
                                        ObjectMapper objectMapper) {
        this(modernTransport, legacyTransport, objectMapper, true);
    }

    public McpProtocolDispatcherServlet(HttpServletMcpTransport modernTransport,
                                        HttpServletStatelessServerTransport legacyTransport,
                                        ObjectMapper objectMapper, boolean legacyEnabled) {
        this.modernTransport = Objects.requireNonNull(modernTransport, "modernTransport must not be null");
        this.legacyTransport = Objects.requireNonNull(legacyTransport, "legacyTransport must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.legacyEnabled = legacyEnabled;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String rpcMethod = null;
        JsonNode body = readCachedBody(request);
        if (body != null) {
            rpcMethod = text(body.get("method"));
        }
        Decision decision = decide(request.getMethod(), request.getHeader(Headers.MCP_METHOD),
                request.getHeader(Headers.MCP_PROTOCOL_VERSION), body);
        if (decision.leg() == Leg.LEGACY && !legacyEnabled) {
            // Legacy 腿已关：记一行 rule=legacy-disabled 的分流日志（流量统计口径
            // 与正常分流一致），再明确拒绝，不转发给任何一条 transport。
            Decision rejected = new Decision(Leg.LEGACY, decision.protocolVersion(), "legacy-disabled");
            logger.info("MCP dispatch leg={} protocolVersion={} httpMethod={} rpcMethod={} rule={}",
                    rejected.leg(), rejected.protocolVersion(), request.getMethod(),
                    rpcMethod == null ? "-" : rpcMethod, rejected.rule());
            rejectLegacyDisabled(response, body);
            return;
        }
        // 每个请求记一行：leg + 协商到的协议版本，便于以后按流量决定何时下掉 legacy.
        logger.info("MCP dispatch leg={} protocolVersion={} httpMethod={} rpcMethod={} rule={}",
                decision.leg(), decision.protocolVersion(), request.getMethod(),
                rpcMethod == null ? "-" : rpcMethod, decision.rule());
        if (decision.leg() == Leg.MODERN) {
            // 显式转型命中 GenericServlet 的 public service 重载（见类注释）。
            modernTransport.service((ServletRequest) request, (ServletResponse) response);
        } else {
            legacyTransport.service((ServletRequest) request, (ServletResponse) response);
        }
    }

    /**
     * Legacy 腿已关闭时的明确拒绝：不转发、不落到 modern、不 500。
     *
     * <p>JSON-RPC 请求（带 {@code id}）答 HTTP 400 + JSON-RPC 错误信封，
     * {@code id} 原样回显；通知（无 {@code id}）按 JSON-RPC 语义无响应，答
     * HTTP 202 空体；无 body 的请求答 HTTP 400 + {@code id: null} 的错误信封。
     * 错误码用 modern {@code McpSchema.ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION}
     * （{@code -32022}，SDK v2.2.0 tag 源码；legacy {@code McpSchema.ErrorCodes}
     * 里没有这个常量，但 -32022 本就是 JSON-RPC 服务端错误段的取值，modern
     * transport 对不支持版本也是 HTTP 400 + -32022，老客户端按版本不支持处理）。</p>
     */
    private void rejectLegacyDisabled(HttpServletResponse response, JsonNode body) throws IOException {
        JsonNode id = body == null ? null : body.get("id");
        if (body != null && id == null) {
            // 通知：JSON-RPC 规定不回响应，只答 202 表示已接收（实际是拒绝执行，
            // 但通知本来就没有响应通道，调用方无从得知，只能记日志——上面已记）。
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return;
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        ObjectNode errorEnvelope = objectMapper.createObjectNode();
        errorEnvelope.put("jsonrpc", "2.0");
        if (id != null) {
            errorEnvelope.set("id", id);
        } else {
            errorEnvelope.putNull("id");
        }
        ObjectNode error = errorEnvelope.putObject("error");
        error.put("code", McpSchema.ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION);
        error.put("message", "Legacy MCP protocol (2025-11-25) is disabled on this server "
                + "(yudao.mcp.server.legacy.enabled=false). "
                + "Use a modern MCP client (2026-07-28) instead.");
        ObjectNode data = error.putObject("data");
        data.put("legacyEnabled", false);
        data.put("legacyProtocolVersion", "2025-11-25");
        data.put("supportedProtocolVersion", MODERN_PROTOCOL_VERSION);
        response.getWriter().write(objectMapper.writeValueAsString(errorEnvelope));
        response.getWriter().flush();
    }

    /**
     * 纯函数分流判定（不碰 servlet 状态，可单测）。
     *
     * @param httpMethod            HTTP 方法
     * @param mcpMethodHeader       {@code Mcp-Method} 头（可为空）
     * @param protocolVersionHeader {@code MCP-Protocol-Version} 头（可为空）
     * @param body                  已解析的 JSON-RPC 请求体（读不到或解析失败时为 null）
     * @return 分流结果
     */
    static Decision decide(String httpMethod, String mcpMethodHeader,
                           String protocolVersionHeader, JsonNode body) {
        if ("GET".equalsIgnoreCase(httpMethod)) {
            // Legacy stateless transport 的 doGet 直接答 405（modern 的 doGet 同样
            // 405）；GET 没有 body 可谈，legacy 是唯一有 legacy 语义的腿。
            return new Decision(Leg.LEGACY, versionOrDash(protocolVersionHeader), "get-legacy");
        }
        if ("DELETE".equalsIgnoreCase(httpMethod)) {
            // Legacy transport 没有 doDelete，HttpServlet 默认答 405；modern 的
            // doDelete 同样 405。
            return new Decision(Leg.LEGACY, versionOrDash(protocolVersionHeader), "delete-legacy");
        }
        if (!"POST".equalsIgnoreCase(httpMethod)) {
            // PUT / OPTIONS / HEAD 等：两腿都不会正常处理，默认 legacy（与其它
            // 未知输入一致），由目标 transport 的 HttpServlet 默认行为答复。
            return new Decision(Leg.LEGACY, versionOrDash(protocolVersionHeader), "default-legacy");
        }
        if (hasText(mcpMethodHeader)) {
            // Mcp-Method 是 2026-07-28 的必备头：modern transport 缺了它就对
            // 非 initialize 请求答 -32020（HEADER_MISMATCH，HTTP 400）。头在，
            // 就是 modern 客户端；头值与体方法不符时由 modern transport 自己拒。
            return new Decision(Leg.MODERN,
                    versionOrDash(protocolVersionHeader, metaProtocolVersion(body),
                            paramsProtocolVersion(body)),
                    "mcp-method-header");
        }
        if (hasText(protocolVersionHeader)) {
            if (MODERN_PROTOCOL_VERSION.equals(protocolVersionHeader.trim())) {
                return new Decision(Leg.MODERN, protocolVersionHeader.trim(),
                        "protocol-version-2026-07-28");
            }
            // 其它值（2025-11-25 及更早、或未知版本）一律 legacy：legacy 是兼容
            // 默认；legacy 服务端从不拒版本，不支持的版本只记一条 warn，再按规
            // 范降级回它支持的最高版本 2025-11-25（HTTP 200），不是 -32022
            //（SDK McpStatelessAsyncServer.asyncInitializeRequestHandler）。
            return new Decision(Leg.LEGACY, protocolVersionHeader.trim(), "protocol-version-legacy");
        }
        String metaVersion = metaProtocolVersion(body);
        if (MODERN_PROTOCOL_VERSION.equals(metaVersion)) {
            return new Decision(Leg.MODERN, metaVersion, "meta-protocol-version-2026-07-28");
        }
        String paramsVersion = paramsProtocolVersion(body);
        if (MODERN_PROTOCOL_VERSION.equals(paramsVersion)) {
            return new Decision(Leg.MODERN, paramsVersion, "params-protocol-version-2026-07-28");
        }
        String rpcMethod = body == null ? null : text(body.get("method"));
        if (McpSchema.METHOD_SERVER_DISCOVER.equals(rpcMethod)) {
            // server/discover 是 modern 协议的方法，legacy 服务端没有这个概念。
            return new Decision(Leg.MODERN, versionOrDash(metaVersion, paramsVersion),
                    "server-discover");
        }
        if (McpSchema.METHOD_INITIALIZE.equals(rpcMethod)) {
            // 走到这里的 initialize 没有任何 modern 信号：legacy 握手。注意 modern
            // transport 自己也有同样的豁免（initialize 且 _meta 无版本时不校验头，
            // 交给 McpServer 答 -32022），但分流到 legacy 才是对的——legacy 客户
            // 端期望的就是 legacy 握手。
            return new Decision(Leg.LEGACY, versionOrDash(metaVersion, paramsVersion), "initialize");
        }
        // 其余 POST（无 modern 头、非 initialize/discover，如 tools/list、
        // tools/call、notifications/*、未知方法、体解析失败）：默认 legacy，
        // 老客户端兼容优先；modern 客户端一定带 Mcp-Method 头，不会落到这里。
        return new Decision(Leg.LEGACY, versionOrDash(metaVersion, paramsVersion), "default-legacy");
    }

    /**
     * 只在请求已被包成 {@code CacheRequestBodyWrapper} 时读体（沿包装链找）。
     * 找不到时返回 null，不消耗原始输入流。
     */
    private JsonNode readCachedBody(HttpServletRequest request) {
        if (!wrapsCachedBody(request)) {
            return null;
        }
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            JsonNode node = objectMapper.readTree(bytes);
            return node != null && node.isObject() ? node : null;
        } catch (IOException | RuntimeException exception) {
            // 体读不到或解析失败不阻塞请求：退回头判定（默认 legacy），由目标
            // transport 对坏体答复（parse error / 415 等）。
            logger.debug("MCP dispatch: failed to read cached request body, falling back to headers: {}",
                    exception.toString());
            return null;
        }
    }

    private static boolean wrapsCachedBody(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current instanceof HttpServletRequestWrapper wrapper) {
            if (wrapper instanceof CacheRequestBodyWrapper) {
                return true;
            }
            if (!(wrapper.getRequest() instanceof HttpServletRequest inner)) {
                return false;
            }
            current = inner;
        }
        return false;
    }

    /**
     * {@code params._meta["io.modelcontextprotocol/protocolVersion"]}。
     * 注意 key 本身含 "/"，不能用 JSON Pointer（"/" 是分隔符），必须逐级 get。
     */
    private static String metaProtocolVersion(JsonNode body) {
        if (body == null) {
            return null;
        }
        JsonNode params = body.get("params");
        if (params == null || !params.isObject()) {
            return null;
        }
        JsonNode meta = params.get("_meta");
        if (meta == null || !meta.isObject()) {
            return null;
        }
        return text(meta.get(MetaKeys.PROTOCOL_VERSION));
    }

    /** Legacy 握手风格的顶层 {@code params.protocolVersion}。 */
    private static String paramsProtocolVersion(JsonNode body) {
        if (body == null) {
            return null;
        }
        JsonNode params = body.get("params");
        if (params == null || !params.isObject()) {
            return null;
        }
        return text(params.get("protocolVersion"));
    }

    private static String versionOrDash(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate.trim();
            }
        }
        return "-";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

}
