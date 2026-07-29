package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthAuthorizationService;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * MCP OAuth 授权码授权。
 *
 * @author bujidao
 */
@Tag(name = "管理后台 - MCP OAuth 授权")
@RestController
@RequestMapping("/mcp/oauth2")
@Validated
public class McpOAuthAuthorizationController {

    @Resource
    private McpOAuthAuthorizationService authorizationService;
    @Resource
    private McpOAuthResourceUriResolver resourceUriResolver;

    @GetMapping("/authorize")
    @Operation(summary = "获得 MCP OAuth 授权信息")
    @Parameter(name = "clientId", required = true, description = "客户端编号", example = "mcp-dcr-xxx")
    public CommonResult<OAuth2OpenAuthorizeInfoRespVO> authorize(@RequestParam("clientId") String clientId) {
        return success(authorizationService.getAuthorizeInfo(clientId));
    }

    @PostMapping("/authorize")
    @Operation(summary = "申请 MCP OAuth 授权")
    @Parameters({
            @Parameter(name = "response_type", required = true, description = "响应类型", example = "code"),
            @Parameter(name = "client_id", required = true, description = "客户端编号", example = "mcp-dcr-xxx"),
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
        return success(authorizationService.approveOrDeny(responseType, clientId, scope, redirectUri,
                autoApprove, state, codeChallenge, codeChallengeMethod, resource,
                resourceUriResolver.resolve(request)));
    }

}
