package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthErrorResponse;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthTokenExchangeService;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2GrantTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP OAuth token endpoint。
 *
 * @author bujidao
 */
@Tag(name = "管理后台 - MCP OAuth Token")
@RestController
@RequestMapping("/mcp/oauth2")
@ConditionalOnProperty(prefix = McpOAuthProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class McpOAuthTokenController {

    @Resource
    private McpOAuthTokenExchangeService tokenExchangeService;
    @Resource
    private McpOAuthResourceUriResolver resourceUriResolver;
    @Resource
    private McpOAuthRateLimiter rateLimiter;

    @PostMapping("/token")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "获得标准 MCP OAuth 访问令牌")
    public ResponseEntity<?> postAccessToken(HttpServletRequest request,
                                             @RequestParam(value = "grant_type", required = false) String grantType,
                                             @RequestParam(value = "client_id", required = false) String clientId,
                                             @RequestParam(value = "code", required = false) String code,
                                             @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                             @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                                             @RequestParam(value = "resource", required = false) String resource,
                                             @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        try {
            checkRateLimit(request);
            if (StrUtil.isBlank(grantType)) {
                // grant_type 不再 required：缺失时 Spring 不抛错，在此按 OAuth 形状返回
                // invalid_request；否则会落入 GlobalExceptionHandler 变成 HTTP 200 + CommonResult。
                throw McpOAuthException.invalidRequest("grant_type is required");
            }
            Object body;
            if (StrUtil.equals(grantType, OAuth2GrantTypeEnum.AUTHORIZATION_CODE.getGrantType())) {
                body = tokenExchangeService.exchangeAuthorizationCode(clientId, code, redirectUri, codeVerifier,
                        resource, resourceUriResolver.resolve(request));
            } else if (StrUtil.equals(grantType, OAuth2GrantTypeEnum.REFRESH_TOKEN.getGrantType())) {
                body = tokenExchangeService.refreshAccessToken(clientId, refreshToken);
            } else {
                throw McpOAuthException.invalidRequest("grant_type is unsupported");
            }
            return noStore(body);
        } catch (McpOAuthException exception) {
            return McpOAuthErrorResponse.error(exception);
        } catch (ServiceException exception) {
            return McpOAuthErrorResponse.error(toOAuthException(exception));
        }
    }

    @PostMapping("/revoke")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "撤销 MCP OAuth 访问令牌")
    public ResponseEntity<?> revokeToken(@RequestParam(value = "client_id", required = false) String clientId,
                                         @RequestParam(value = "token", required = false) String token) {
        try {
            if (StrUtil.isBlank(token)) {
                // 与 grant_type 同理：缺失时不走 Spring 的参数缺失异常，按 OAuth 形状返回。
                throw McpOAuthException.invalidRequest("token is required");
            }
            tokenExchangeService.revokeToken(clientId, token);
            return noStore(Map.of());
        } catch (McpOAuthException exception) {
            return McpOAuthErrorResponse.error(exception);
        } catch (ServiceException exception) {
            return McpOAuthErrorResponse.error(toOAuthException(exception));
        }
    }

    /**
     * 上游 ServiceException → 标准 OAuth 错误：上游错误码是内部口径（OAUTH2_CLIENT_* /
     * OAUTH2_CODE_* / 通用 400 / 401），直接漏出会被 GlobalExceptionHandler 包成
     * HTTP 200 + CommonResult，合规客户端认不出来。在此按 RFC 6749 §5.2 映射：
     * <ul>
     * <li>客户端不存在 / 已禁用 / secret 错 → invalid_client</li>
     * <li>客户端无权用该 grant_type → unauthorized_client</li>
     * <li>授权范围过大 → invalid_scope</li>
     * <li>授权码不存在 / 已过期、redirect_uri 不匹配 → invalid_grant（RFC 6749 §5.2
     * 明确把 redirect_uri 不匹配归入 invalid_grant）</li>
     * <li>通用 401（过期 refresh 等）→ invalid_grant</li>
     * <li>通用 400：经本端点只可能是 refresh 令牌问题（不存在 / 不属于该客户端），
     * 故 → invalid_grant；clientDO 为 null 的 400 在此不可达（客户端已先校验）</li>
     * <li>其余未知码 → invalid_request；当前调用图里全部可达码都已被上面覆盖，
     * 兜底实际不可达，若将来有 500 类码漏进来应再映为 server_error 而不是 400</li>
     * </ul>
     */
    private static McpOAuthException toOAuthException(ServiceException exception) {
        Integer code = exception.getCode();
        String message = exception.getMessage();
        if (ErrorCodeConstants.OAUTH2_CLIENT_NOT_EXISTS.getCode().equals(code)
                || ErrorCodeConstants.OAUTH2_CLIENT_DISABLE.getCode().equals(code)
                || ErrorCodeConstants.OAUTH2_CLIENT_CLIENT_SECRET_ERROR.getCode().equals(code)) {
            return McpOAuthException.invalidClient(message);
        }
        if (ErrorCodeConstants.OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS.getCode().equals(code)) {
            return McpOAuthException.unauthorizedClient(message);
        }
        if (ErrorCodeConstants.OAUTH2_CLIENT_SCOPE_OVER.getCode().equals(code)) {
            return McpOAuthException.invalidScope(message);
        }
        if (ErrorCodeConstants.OAUTH2_CODE_NOT_EXISTS.getCode().equals(code)
                || ErrorCodeConstants.OAUTH2_CODE_EXPIRE.getCode().equals(code)
                || ErrorCodeConstants.OAUTH2_CLIENT_REDIRECT_URI_NOT_MATCH.getCode().equals(code)
                || GlobalErrorCodeConstants.UNAUTHORIZED.getCode().equals(code)
                || GlobalErrorCodeConstants.BAD_REQUEST.getCode().equals(code)) {
            return McpOAuthException.invalidGrant(message);
        }
        return McpOAuthException.invalidRequest(message);
    }

    private static ResponseEntity<Object> noStore(Object body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    /**
     * token 兑换限流：按调用方 IP 计，超限抛 OAuth {@code temporarily_unavailable}（429，
     * 带 {@code Retry-After: 60}），由本方法的 catch 转成标准 OAuth JSON 错误；只记 IP 与结果，
     * 不记 client_id / code / refresh_token 等请求参数。
     * rateLimiter 由 Spring 注入；手工 new 出来的 Controller（测试）没有它时不限流，
     * 生产走 Spring 容器，一定有 Bean（与本控制器用同一个 enabled 开关）。
     * 注意：只限兑换入口（/token），不 limit /revoke——吊销是安全操作，不应被限流挡住。
     */
    private void checkRateLimit(HttpServletRequest request) {
        if (rateLimiter == null || !rateLimiter.isEnabled()) {
            return;
        }
        String clientIp = ServletUtils.getClientIP(request);
        if (!rateLimiter.tryAcquireToken(clientIp)) {
            log.warn("[token][ip({})] MCP OAuth token rate limited", clientIp);
            throw McpOAuthException.rateLimited("too many requests, please retry later");
        }
    }

}
