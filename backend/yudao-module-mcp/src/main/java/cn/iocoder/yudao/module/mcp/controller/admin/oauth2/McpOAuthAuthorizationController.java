package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthRateLimiter;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthAuthorizationService;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * MCP OAuth 授权码授权。
 *
 * @author bujidao
 */
@Tag(name = "管理后台 - MCP OAuth 授权")
@RestController
@RequestMapping("/mcp/oauth2")
@ConditionalOnProperty(prefix = McpOAuthProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Validated
@Slf4j
public class McpOAuthAuthorizationController {

    @Resource
    private McpOAuthAuthorizationService authorizationService;
    @Resource
    private McpOAuthResourceUriResolver resourceUriResolver;
    @Resource
    private McpOAuthRateLimiter rateLimiter;

    @GetMapping("/authorize")
    @Operation(summary = "获得 MCP OAuth 授权信息")
    @Parameter(name = "clientId", required = true, description = "客户端编号（CIMD 客户端为 client_id URL；历史 mcp-dcr- 编号仍可用）", example = "https://client.example.com/mcp-client")
    public CommonResult<OAuth2OpenAuthorizeInfoRespVO> authorize(@RequestParam("clientId") String clientId) {
        // 有意不限流：仅取授权信息、不发授权码；限流只管发码入口 POST /authorize。
        return success(authorizationService.getAuthorizeInfo(clientId));
    }

    @PostMapping("/authorize")
    @Operation(summary = "申请 MCP OAuth 授权")
    @Parameters({
            @Parameter(name = "response_type", required = true, description = "响应类型", example = "code"),
            @Parameter(name = "client_id", required = true, description = "客户端编号（CIMD 客户端为 client_id URL；历史 mcp-dcr- 编号仍可用）", example = "https://client.example.com/mcp-client"),
            @Parameter(name = "scope", description = "授权范围"),
            @Parameter(name = "redirect_uri", required = true, description = "重定向 URI"),
            @Parameter(name = "auto_approve", required = true, description = "用户是否接受", example = "true"),
            @Parameter(name = "state"),
            @Parameter(name = "code_challenge", required = true),
            @Parameter(name = "code_challenge_method", required = true, example = "S256"),
            @Parameter(name = "resource", required = true)
    })
    public CommonResult<String> approveOrDeny(HttpServletRequest request,
                                              @RequestParam("response_type") String responseType,
                                              @RequestParam("client_id") String clientId,
                                              @RequestParam(value = "scope", required = false) String scope,
                                              @RequestParam("redirect_uri") String redirectUri,
                                              @RequestParam(value = "auto_approve") Boolean autoApprove,
                                              @RequestParam(value = "state", required = false) String state,
                                              @RequestParam(value = "code_challenge") String codeChallenge,
                                              @RequestParam(value = "code_challenge_method") String codeChallengeMethod,
                                              @RequestParam(value = "resource") String resource) {
        checkRateLimit(request);
        return success(authorizationService.approveOrDeny(responseType, clientId, scope, redirectUri,
                autoApprove, state, codeChallenge, codeChallengeMethod, resource,
                resourceUriResolver.resolve(request)));
    }

    /**
     * 授权同意限流：按调用方 IP 计，超限抛 ServiceException(TOO_MANY_REQUESTS, 429)，
     * 由 GlobalExceptionHandler 的 serviceExceptionHandler 分支转成 CommonResult(429)，
     * 不落到 500 兜底；只记 IP 与结果，不记 client_id / code 等请求参数。
     * 注意：本端点返回 CommonResult 约定，不走 OAuth JSON 错误，所以这里不用
     * McpOAuthException（GlobalExceptionHandler 没有它的分支，会变成 500）。
     * rateLimiter 由 Spring 注入；手工 new 出来的 Controller（测试）没有它时不限流，
     * 生产走 Spring 容器，一定有 Bean（与本控制器用同一个 enabled 开关）。
     */
    private void checkRateLimit(HttpServletRequest request) {
        if (rateLimiter == null || !rateLimiter.isEnabled()) {
            return;
        }
        String clientIp = ServletUtils.getClientIP(request);
        if (!rateLimiter.tryAcquireAuthorize(clientIp)) {
            log.warn("[authorize][ip({})] MCP OAuth authorize rate limited", clientIp);
            throw exception(GlobalErrorCodeConstants.TOO_MANY_REQUESTS);
        }
    }

}
