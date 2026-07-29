package cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * MCP OAuth Dynamic Client Registration 请求。
 *
 * @author bujidao
 */
@Data
public class McpOAuthClientRegistrationReqVO {

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("client_uri")
    private String clientUri;

    @JsonProperty("logo_uri")
    private String logoUri;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    @JsonProperty("response_types")
    private List<String> responseTypes;

    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    @JsonProperty("application_type")
    private String applicationType;

    /**
     * RFC 7591 使用空格分隔的 scope 字符串。
     */
    private String scope;

}
