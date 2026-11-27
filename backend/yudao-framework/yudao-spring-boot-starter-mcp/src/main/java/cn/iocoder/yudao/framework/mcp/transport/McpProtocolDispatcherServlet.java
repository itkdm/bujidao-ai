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
import java.util.Set;

/**
 * /mcp 的双协议分流入口：同一 endpoint 同时服务 MCP 2026-07-28（modern）与
 * 2025-11-25（legacy stateless）。
 *
 * <p>只做分流，不复制认证逻辑、不碰安全链：认证、scope、租户、字符集等过滤器
 * 都在分流器之前跑完，两条腿共用同一套。/mcp 路径上没有 QPS 限流（限流阈值只属于
 * OAuth 端点），不要把体大小限制说成限流。分流器拿到请求后直接调目标 transport 的
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
 * <p>判据（按顺序命中即分流，claim 主规则）：GET / DELETE / 其它非 POST 一律 legacy；
 * POST 先看 {@code MCP-Protocol-Version} 头——值精确等于 2026-07-28 的 modern，其它
 * 有值一律 legacy（头优先：头体冲突时以头为准，进 modern 的冲突请求由 modern 腿的
 * SDK {@code validateHeaders} 二次校验以 -32020 拒绝，不会静默执行）；头无值时看体
 * {@code params._meta} 里有没有保留键
 * {@code io.modelcontextprotocol/protocolVersion}——键存在（无论值是什么）即 modern，
 * 不支持的版本由 modern 腿答 -32022；其余 POST 默认 legacy（老客户端兼容优先）。
 * {@code Mcp-Method} 头不参与分类（它只是 modern 腿的校验对象，不是分类信号）、
 * 方法名不参与分类（合规 modern 探针一定带 envelope claim，会由 claim 规则认出来；
 * 无 claim 的 {@code server/discover} 与 modern 形态调用一样落默认 legacy）、
 * 顶层 {@code params.protocolVersion} 不参与分类（那是 legacy {@code initialize}
 * 的参数，带 2026-07-28 的 initialize 走 legacy 协商降级回 2025-11-25）。
 * 路由与下游同为精确匹配：头值、体版本都不 trim，下游 SDK
 * {@code protocolVersionFromMeta} 原样返回、{@code validateHeaders} 用 equals 比对、
 * {@code supportedVersions.contains} 精确匹配。</p>
 *
 * <p>读体只走已有的 body 缓存：全局 {@code CacheRequestBodyFilter}（/*，JSON 请求）
 * 已把请求包成 {@code CacheRequestBodyWrapper}，其 {@code getInputStream()} 每次
 * 返回新的流，可反复读；分流器沿包装链找到它才读体，找不到（非 JSON、或 web starter
 * 未启用）时只按头判定、绝不消耗原始输入流。分流后把同一个（缓存过的）请求交给
 * transport，transport 还能再读到完整 body。</p>
 *
 * <p>边界：未知 HTTP 方法、未知 JSON-RPC 方法、无头 POST、重复 initialize、
 * {@code Mcp-Session-Id} 头——前三者都落到默认 legacy（modern 腿不建 session，
 * session 头不是分流信号，目标服务端自己决定怎么答）；重复 initialize 由目标
 * 服务端按自己的无状态语义处理，分流器不拦截。已知限制：无头且无 {@code _meta}
 * 版本的 modern 形态调用（如 tools/* 且体里只有方法名）也会落到默认 legacy，
 * 有意不加启发式规则——WorkBuddy 等 legacy 客户端实际发的 {@code _meta} 未核实，
 * 凭体方法 + clientCapabilities 猜 modern 有误伤 legacy 客户端的风险。</p>
 *
 * <p>Legacy 开关（{@code yudao.mcp.server.legacy.enabled}，默认 true）：关掉后
 * 分流到 legacy 的 <b>POST</b> 请求在这里被明确拒绝，不转发给任何一条 transport——
 * 既不静默落到 modern，也不产生 500。拒绝形态：JSON-RPC 请求（带 {@code id}）答
 * HTTP 400 + JSON-RPC 错误信封（{@code code -32022}，与 modern transport 对不
 * 支持协议版本的回答一致，见 SDK v2.2.0
 * {@code HttpServletMcpTransport.DEFAULT_ERROR_STATUSES}）；通知（无 {@code id}）
 * 按 JSON-RPC 语义无响应，答 HTTP 202；无 body 的 POST 请求答 HTTP 400 +
 * {@code id: null} 的错误信封。GET / DELETE / 未知方法绕过开关，直接转发给 legacy
 * transport 由它答 405——它们根本不是 legacy 协议请求，开关关闭时把它包装成
 * “legacy 已关闭、请用 modern 客户端”是语义误导。Modern 请求不受开关影响。
 * 拒绝的那一行分流日志记为 {@code rule=legacy-disabled}，便于以后按流量决定何时
 * 下掉 legacy。</p>
 *
 * @author bujidao
 */
public class McpProtocolDispatcherServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(McpProtocolDispatcherServlet.class);

    /** Modern 协议版本：SDK v2.2.0 的 {@code McpSchema.LATEST_PROTOCOL_VERSION}。 */
    static final String MODERN_PROTOCOL_VERSION = McpSchema.LATEST_PROTOCOL_VERSION;

    /** Legacy 协议版本：SDK v2.2.0 的 legacy stateless 服务端支持的最高版本。 */
    static final String LEGACY_PROTOCOL_VERSION = "2025-11-25";

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
        Decision decision = decide(request.getMethod(),
                request.getHeader(Headers.MCP_PROTOCOL_VERSION), body);
        if (decision.leg() == Leg.LEGACY && !legacyEnabled
                && "POST".equalsIgnoreCase(request.getMethod())) {
            // Legacy 腿已关：只拦截 POST——GET / DELETE / 未知方法不是 legacy
            // 协议请求，绕过开关直接转发给 legacy transport 由它答 405（见类注释）。
            // Legacy 腿已关：记一行 rule=legacy-disabled 的分流日志（流量统计口径
            // 与正常分流一致），再明确拒绝，不转发给任何一条 transport。
            Decision rejected = new Decision(Leg.LEGACY, decision.protocolVersion(), "legacy-disabled");
            logger.info("MCP dispatch leg={} protocolVersion={} httpMethod={} rpcMethod={} rule={}",
                    rejected.leg(), loggableProtocolVersion(rejected.protocolVersion()),
                    request.getMethod(),
                    loggableRpcMethod(rpcMethod), rejected.rule());
            rejectLegacyDisabled(response, body);
            return;
        }
        // 每个请求记一行：leg + 协商到的协议版本，便于以后按流量决定何时下掉 legacy.
        // 协议版本记 loggableProtocolVersion() 处理过的值，不记请求头原文（见该方法注释）。
        logger.info("MCP dispatch leg={} protocolVersion={} httpMethod={} rpcMethod={} rule={}",
                decision.leg(), loggableProtocolVersion(decision.protocolVersion()),
                request.getMethod(),
                loggableRpcMethod(rpcMethod), decision.rule());
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
     * HTTP 202 空体；无 body 的 POST 请求答 HTTP 400 + {@code id: null} 的错误信封。
     * 本方法只对 POST 调用（见 {@link #service}）：GET / DELETE / 未知方法绕过
     * kill-switch，由 legacy transport 答 405。
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
        data.put("legacyProtocolVersion", LEGACY_PROTOCOL_VERSION);
        data.put("supportedProtocolVersion", MODERN_PROTOCOL_VERSION);
        response.getWriter().write(objectMapper.writeValueAsString(errorEnvelope));
        response.getWriter().flush();
    }

    /**
     * 纯函数分流判定（不碰 servlet 状态，可单测）。
     *
     * <p>判据只有一条 claim 主规则加方法默认：非 POST 一律 legacy；POST 先看
     * {@code MCP-Protocol-Version} 头（头优先，精确匹配，不 trim），再看体
     * {@code params._meta} 有没有协议版本保留键（键存在即 modern，不看值、不 trim），
     * 其余默认 legacy。{@code Mcp-Method} 头、方法名、顶层
     * {@code params.protocolVersion} 都不参与分类（见类注释）。</p>
     *
     * @param httpMethod            HTTP 方法
     * @param protocolVersionHeader {@code MCP-Protocol-Version} 头（可为空）
     * @param body                  已解析的 JSON-RPC 请求体（读不到或解析失败时为 null）
     * @return 分流结果
     */
    static Decision decide(String httpMethod, String protocolVersionHeader, JsonNode body) {
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
        if (MODERN_PROTOCOL_VERSION.equals(protocolVersionHeader)) {
            // 头值精确等于 2026-07-28（不 trim）：modern。头体冲突时头优先——
            // 体 claim 是其它版本时仍走 modern，由 modern 腿 validateHeaders
            // 以 -32020 拒绝，不静默执行；无体 claim 时 modern 腿同样会因缺
            // envelope 拒绝，最终效果与 TS SDK 一致。
            return new Decision(Leg.MODERN, protocolVersionHeader, "protocol-version-2026-07-28");
        }
        if (hasText(protocolVersionHeader)) {
            // 其它头值（2025-11-25 及更早、或未知版本）一律 legacy：legacy 是兼容
            // 默认；legacy 服务端从不拒版本，不支持的版本只记一条 warn，再按规
            // 范降级回它支持的最高版本 2025-11-25（HTTP 200），不是 -32022
            //（SDK McpStatelessAsyncServer.asyncInitializeRequestHandler）。
            return new Decision(Leg.LEGACY, protocolVersionHeader, "protocol-version-legacy");
        }
        String metaVersion = metaProtocolVersion(body);
        if (metaVersion != null) {
            // Claim 主规则：params._meta 里有协议版本保留键即 modern，不看值——
            // 2026-07-28 之外的版本（未知版本、旧版本）同样进 modern，由 modern
            // 腿答 -32022。非文本的 claim 值不算（SDK protocolVersionFromMeta
            // 同样只认 String）。
            return new Decision(Leg.MODERN, metaVersion, "meta-protocol-version-claim");
        }
        // 其余 POST（无头、无 claim，如无 envelope 的 initialize、tools/list、
        // tools/call、notifications/*、未知方法、体解析失败）：默认 legacy，
        // 老客户端兼容优先。无 claim 的 initialize 自然落在这里，不需要单独的
        // initialize 分支；带 2026-07-28 的顶层 params.protocolVersion 同样
        // 落在这里——那是 legacy initialize 的参数，走 legacy 协商降级。
        // 已知限制：无头、无 _meta 版本的 modern 形态调用也会落在这里（见类注释），
        // 有意不用体方法启发式分流。
        return new Decision(Leg.LEGACY, versionOrDash(metaVersion), "default-legacy");
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
     * {@code params._meta["io.modelcontextprotocol/protocolVersion"]}，原样返回，不 trim：
     * 路由与下游同为精确匹配（见类注释），下游 SDK {@code protocolVersionFromMeta}
     * 也是原样返回。非文本值返回 null（SDK 同样只认 String）。
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

    /**
     * 日志里的 RPC 方法名只记已知方法（modern 与 legacy 两套 SDK {@code McpSchema}
     * 的方法常量全集），未知记长度——与 {@link #loggableProtocolVersion(String)}
     * 同一口径。方法名来自请求体，是攻击者可控字符串：体里的 {@code \r} / {@code \n}
     * 转义经 Jackson 解码后就是真换行，容器层拦裸 CRLF 头的理由对体值不成立，
     * 所以不记原文；已知方法名是固定常量，不含控制字符。注意 legacy 的
     * {@code METHOD_PROMPT_LIST} / {@code METHOD_PROMPT_GET} 与 modern 同值
     * （"prompts/list" / "prompts/get"），{@code Set.of} 不接受重复元素，故不重复列出。
     */
    private static final Set<String> KNOWN_RPC_METHODS = Set.of(
            McpSchema.METHOD_SERVER_DISCOVER,
            McpSchema.METHOD_INITIALIZE,
            McpSchema.METHOD_TOOLS_LIST,
            McpSchema.METHOD_TOOLS_CALL,
            McpSchema.METHOD_RESOURCES_LIST,
            McpSchema.METHOD_RESOURCES_TEMPLATES_LIST,
            McpSchema.METHOD_RESOURCES_READ,
            McpSchema.METHOD_PROMPTS_LIST,
            McpSchema.METHOD_PROMPTS_GET,
            McpSchema.METHOD_COMPLETION_COMPLETE,
            McpSchema.METHOD_SUBSCRIPTIONS_LISTEN,
            McpSchema.METHOD_NOTIFICATION_CANCELLED,
            McpSchema.METHOD_NOTIFICATION_PROGRESS,
            McpSchema.METHOD_NOTIFICATION_MESSAGE,
            McpSchema.METHOD_NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED,
            McpSchema.METHOD_NOTIFICATION_RESOURCES_UPDATED,
            McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED,
            McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED,
            McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED,
            McpSchema.METHOD_ELICITATION_CREATE,
            McpSchema.METHOD_SAMPLING_CREATE_MESSAGE,
            McpSchema.METHOD_ROOTS_LIST,
            io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_INITIALIZED,
            io.modelcontextprotocol.spec.McpSchema.METHOD_PING,
            io.modelcontextprotocol.spec.McpSchema.METHOD_RESOURCES_SUBSCRIBE,
            io.modelcontextprotocol.spec.McpSchema.METHOD_RESOURCES_UNSUBSCRIBE,
            io.modelcontextprotocol.spec.McpSchema.METHOD_LOGGING_SET_LEVEL,
            io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED,
            io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_ELICITATION_COMPLETE);

    private static String loggableRpcMethod(String rpcMethod) {
        if (rpcMethod == null) {
            return "-";
        }
        if (KNOWN_RPC_METHODS.contains(rpcMethod)) {
            return rpcMethod;
        }
        return "unknown(len=" + rpcMethod.length() + ")";
    }

    /**
     * 日志里的协议版本只记“已知值或未知 + 长度”：{@code MCP-Protocol-Version}
     * 头是攻击者可控字符串，直接记原文会把任意长字符串写进日志；已知版本
     * （modern / legacy）与认不出来（"-"）原样记，未知值只记长度。容器层按
     * RFC 7230 拦截含裸 CRLF 的头，这里不再另做字符转义。
     */
    private static String loggableProtocolVersion(String protocolVersion) {
        if (protocolVersion == null) {
            return "-";
        }
        if (MODERN_PROTOCOL_VERSION.equals(protocolVersion)
                || LEGACY_PROTOCOL_VERSION.equals(protocolVersion)
                || "-".equals(protocolVersion)) {
            return protocolVersion;
        }
        return "unknown(len=" + protocolVersion.length() + ")";
    }

    private static String versionOrDash(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                // 不 trim：路由精确匹配，下游同样精确校验（见类注释）。
                return candidate;
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
