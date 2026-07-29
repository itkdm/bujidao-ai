package cn.iocoder.yudao.module.system.controller.admin.oauth2;

import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2ClientRegistrationReqVO;
import cn.iocoder.yudao.module.system.controller.admin.oauth2.vo.open.OAuth2ClientRegistrationRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.framework.oauth2.config.OAuth2DynamicClientRegistrationProperties;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2ClientService;
import cn.iocoder.yudao.module.system.service.oauth2.dto.OAuth2DynamicClientRegistrationCreateReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2ClientRegistrationControllerTest {

    private final OAuth2ClientService oauth2ClientService = mock(OAuth2ClientService.class);
    private final OAuth2DynamicClientRegistrationProperties properties =
            new OAuth2DynamicClientRegistrationProperties();
    private final OAuth2ClientRegistrationController controller = new OAuth2ClientRegistrationController();

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setAllowPrivateUseUriSchemeRedirects(true);
        ReflectionTestUtils.setField(controller, "oauth2ClientService", oauth2ClientService);
        ReflectionTestUtils.setField(controller, "properties", properties);
        when(oauth2ClientService.getOAuth2ClientFromCache(any())).thenReturn(null);
        when(oauth2ClientService.createDynamicOAuth2Client(any())).thenAnswer(invocation -> {
            OAuth2DynamicClientRegistrationCreateReqDTO req = invocation.getArgument(0);
            return new OAuth2ClientDO()
                    .setClientId(req.getClientId())
                    .setName(req.getClientName())
                    .setRedirectUris(req.getRedirectUris());
        });
    }

    @Test
    void shouldRegisterPublicClientWithPkce() {
        OAuth2ClientRegistrationReqVO reqVO = new OAuth2ClientRegistrationReqVO()
                .setClientName("WorkBuddy Connector")
                .setRedirectUris(List.of("workbuddy://workbuddy/mcp/demo/oauth/callback",
                        "http://127.0.0.1:5173/oauth/callback"))
                .setGrantTypes(List.of("authorization_code", "refresh_token"))
                .setResponseTypes(List.of("code"))
                .setTokenEndpointAuthMethod("none")
                .setApplicationType("native")
                .setScope("mcp:access");

        ResponseEntity<?> response = controller.register(reqVO);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        OAuth2ClientRegistrationRespVO body = (OAuth2ClientRegistrationRespVO) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getClientId()).startsWith("mcp-dcr-");
        assertThat(body.getTokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(body.getScope()).isEqualTo("mcp:access");

        ArgumentCaptor<OAuth2DynamicClientRegistrationCreateReqDTO> captor =
                ArgumentCaptor.forClass(OAuth2DynamicClientRegistrationCreateReqDTO.class);
        org.mockito.Mockito.verify(oauth2ClientService).createDynamicOAuth2Client(captor.capture());
        assertThat(captor.getValue().getAutoApproveScopes()).isEmpty();
        assertThat(captor.getValue().getAdditionalInformation())
                .contains("\"dynamic_client_registration\":true")
                .contains("\"require_pkce\":true");
    }

    @Test
    void shouldRejectUnsupportedScope() {
        OAuth2ClientRegistrationReqVO reqVO = new OAuth2ClientRegistrationReqVO()
                .setRedirectUris(List.of("http://127.0.0.1:5173/oauth/callback"))
                .setScope("mcp:access admin");

        ResponseEntity<?> response = controller.register(reqVO);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(errorBody(response)).containsEntry("error", "invalid_scope");
    }

    @Test
    void shouldRejectCustomSchemeWhenDisabled() {
        properties.setAllowPrivateUseUriSchemeRedirects(false);
        OAuth2ClientRegistrationReqVO reqVO = new OAuth2ClientRegistrationReqVO()
                .setRedirectUris(List.of("workbuddy://workbuddy/mcp/demo/oauth/callback"));

        ResponseEntity<?> response = controller.register(reqVO);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(errorBody(response)).containsEntry("error", "invalid_redirect_uri");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorBody(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

}
