package cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OAuth2 Dynamic Client Registration 响应。
 *
 * @author bujidao
 */
@Data
public class OAuth2ClientRegistrationRespVO {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_id_issued_at")
    private Long clientIdIssuedAt;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    @JsonProperty("response_types")
    private List<String> responseTypes;

    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    @JsonProperty("application_type")
    private String applicationType;

    private String scope;

}
