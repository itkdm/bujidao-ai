package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthErrorResponse;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthTokenExchangeService;
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
                                             @RequestParam("grant_type") String grantType,
                                             @RequestParam(value = "client_id", required = false) String clientId,
                                             @RequestParam(value = "code", required = false) String code,
                                             @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                             @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                                             @RequestParam(value = "resource", required = false) String resource,
                                             @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        try {
            checkRateLimit(request);
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
        }
    }

    @PostMapping("/revoke")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "撤销 MCP OAuth 访问令牌")
    public ResponseEntity<?> revokeToken(@RequestParam(value = "client_id", required = false) String clientId,
                                         @RequestParam("token") String token) {
        try {
            tokenExchangeService.revokeToken(clientId, token);
            return noStore(Map.of());
        } catch (McpOAuthException exception) {
            return McpOAuthErrorResponse.error(exception);
        }
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
