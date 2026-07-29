package cn.iocoder.yudao.module.mcp.service.oauth2;

import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationReqVO;
import cn.iocoder.yudao.module.mcp.controller.admin.oauth2.vo.McpOAuthClientRegistrationRespVO;
import cn.iocoder.yudao.module.mcp.framework.oauth2.config.McpOAuthProperties;
import cn.iocoder.yudao.module.mcp.framework.oauth2.core.McpOAuthException;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.OAuth2ClientMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpOAuthClientRegistrationServiceTest {

    @Mock
    private OAuth2ClientMapper oauth2ClientMapper;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    private McpOAuthProperties properties;
    private McpOAuthClientRegistrationService service;

    @BeforeEach
    void setUp() {
        properties = new McpOAuthProperties();
        service = new McpOAuthClientRegistrationService(properties, oauth2ClientMapper, cacheManager);
    }

    @Test
    void shouldCreatePublicPkceClientWithDefaultScope() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setClientName("Codex");
        reqVO.setRedirectUris(List.of("http://127.0.0.1:10244/callback/test"));
        when(oauth2ClientMapper.selectByClientId(anyString())).thenReturn(null);
        when(cacheManager.getCache(RedisKeyConstants.OAUTH_CLIENT)).thenReturn(cache);

        McpOAuthClientRegistrationRespVO response = service.register(reqVO, "http://127.0.0.1:48080/mcp");

        ArgumentCaptor<OAuth2ClientDO> clientCaptor = ArgumentCaptor.forClass(OAuth2ClientDO.class);
        verify(oauth2ClientMapper).insert(clientCaptor.capture());
        verify(cache).clear();
        OAuth2ClientDO client = clientCaptor.getValue();
        assertThat(response.getClientId()).isEqualTo(client.getClientId()).startsWith("mcp-dcr-");
        assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(response.getScope()).isEqualTo("mcp:access");
        assertThat(client.getSecret()).isEmpty();
        assertThat(client.getScopes()).containsExactly("mcp:access");
        assertThat(client.getAutoApproveScopes()).isEmpty();
        assertThat(client.getAuthorizedGrantTypes()).containsExactly("authorization_code");
        assertThat(client.getAdditionalInformation())
                .contains("\"token_endpoint_auth_method\":\"none\"")
                .contains("\"public_client\":true")
                .contains("\"require_pkce\":true")
                .contains("\"mcp_resource\":\"http://127.0.0.1:48080/mcp\"");
    }

    @Test
    void shouldRejectUnsupportedRedirectUri() {
        McpOAuthClientRegistrationReqVO reqVO = new McpOAuthClientRegistrationReqVO();
        reqVO.setRedirectUris(List.of("javascript:alert(1)"));

        assertThatThrownBy(() -> service.register(reqVO, "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "invalid_redirect_uri");
    }

    @Test
    void shouldRejectRegistrationWhenDcrIsDisabled() {
        properties.setDynamicClientRegistrationEnabled(false);

        assertThatThrownBy(() -> service.register(new McpOAuthClientRegistrationReqVO(),
                "http://127.0.0.1:48080/mcp"))
                .isInstanceOf(McpOAuthException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.NOT_FOUND, "invalid_request");
    }

}
