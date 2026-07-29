package cn.iocoder.yudao.module.mcp.controller.admin.oauth2;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationReqVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthErrorResponse;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthResourceUriResolver;
import cn.iocoder.yudao.module.mcp.service.oauth2.McpOAuthClientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
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
public class McpOAuthClientRegistrationController {

    @Resource
    private McpOAuthClientRegistrationService clientRegistrationService;
    @Resource
    private McpOAuthResourceUriResolver resourceUriResolver;

    @PostMapping("/register")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "动态注册 MCP OAuth 客户端")
    public ResponseEntity<?> register(HttpServletRequest request,
                                      @RequestBody(required = false) McpOAuthClientRegistrationReqVO reqVO) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(clientRegistrationService.register(reqVO, resourceUriResolver.resolve(request)));
        } catch (McpOAuthException exception) {
            return McpOAuthErrorResponse.error(exception.getStatus(), exception.getError(), exception.getDescription());
        }
    }

}
