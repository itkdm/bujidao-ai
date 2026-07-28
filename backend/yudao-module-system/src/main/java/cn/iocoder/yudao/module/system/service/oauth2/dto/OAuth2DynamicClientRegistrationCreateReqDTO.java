package cn.iocoder.yudao.module.system.service.oauth2.dto;

import lombok.Data;

import java.util.List;

/**
 * OAuth2 动态客户端注册创建 DTO。
 *
 * @author bujidao
 */
@Data
public class OAuth2DynamicClientRegistrationCreateReqDTO {

    private String clientId;

    private String clientName;

    private String logoUri;

    private String description;

    private List<String> redirectUris;

    private List<String> authorizedGrantTypes;

    private List<String> scopes;

    private List<String> autoApproveScopes;

    private List<String> resourceIds;

    private String additionalInformation;

    private Integer accessTokenValiditySeconds;

    private Integer refreshTokenValiditySeconds;

}
