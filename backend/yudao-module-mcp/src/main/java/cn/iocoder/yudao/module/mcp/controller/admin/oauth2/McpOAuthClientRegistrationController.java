package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationReqVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthErrorResponse;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthClientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP OAuth Dynamic Client Registration。
 *
 * @author bujidao
 */
@Tag(name = "管理后台 - MCP OAuth 动态客户端注册")
@RestController
@RequestMapping("/mcp/oauth2")
@ConditionalOnProperty(prefix = McpOAuthProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class McpOAuthClientRegistrationController {

    @Resource
    private McpOAuthClientRegistrationService clientRegistrationService;
    @Resource
    private McpOAuthResourceUriResolver resourceUriResolver;
    @Resource
    private McpOAuthRateLimiter rateLimiter;

    @PostMapping("/register")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "动态注册 MCP OAuth 客户端")
    public ResponseEntity<?> register(HttpServletRequest request,
                                      @RequestBody(required = false) McpOAuthClientRegistrationReqVO reqVO) {
        try {
            checkRateLimit(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(clientRegistrationService.register(reqVO, resourceUriResolver.resolve(request)));
        } catch (McpOAuthException exception) {
            return McpOAuthErrorResponse.error(exception);
        }
    }

    /**
     * 动态注册限流：按调用方 IP 计，超限抛 OAuth {@code temporarily_unavailable}（429，
     * 带 {@code Retry-After: 60}），由本方法的 catch 转成标准 OAuth JSON 错误；只记 IP 与结果，
     * 不记 redirect_uri / client_name 等请求参数。
     * rateLimiter 由 Spring 注入；手工 new 出来的 Controller（测试）没有它时不限流，
     * 生产走 Spring 容器，一定有 Bean（与本控制器用同一个 enabled 开关）。
     * 注意：限流依赖调用方真实 IP；{@code ServletUtils.getClientIP} 优先读 X-Forwarded-For
     * 首值，部署时须由可信代理剥离/重写该头，否则攻击者自带 XFF 可轮换 IP 绕过限流。
     */
    private void checkRateLimit(HttpServletRequest request) {
        if (rateLimiter == null || !rateLimiter.isEnabled()) {
            return;
        }
        String clientIp = ServletUtils.getClientIP(request);
        if (!rateLimiter.tryAcquireRegister(clientIp)) {
            log.warn("[register][ip({})] MCP OAuth client registration rate limited", clientIp);
            throw McpOAuthException.rateLimited("too many requests, please retry later");
        }
    }

}
