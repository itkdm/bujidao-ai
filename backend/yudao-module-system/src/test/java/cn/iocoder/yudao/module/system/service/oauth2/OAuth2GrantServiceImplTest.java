package cn.iocoder.yudao.module.system.service.oauth2;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AuthorizationCodeExtraDO;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AuthorizationCodeExtraRedisDAO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_CODE_VERIFIER_MISMATCH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OAuth2GrantServiceImpl} 的单元测试
 *
 * @author 芋道源码
 */
public class OAuth2GrantServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private OAuth2GrantServiceImpl oauth2GrantService;

    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private OAuth2CodeService oauth2CodeService;
    @Mock
    private AdminAuthService adminAuthService;
    @Mock
    private OAuth2AuthorizationCodeExtraRedisDAO oauth2AuthorizationCodeExtraRedisDAO;

    @Test
    public void testGrantImplicit() {
        // 准备参数
        Long userId = randomLongId();
        Integer userType = randomEle(UserTypeEnum.values()).getValue();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        // mock 方法
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.createAccessToken(eq(userId), eq(userType),
                eq(clientId), eq(scopes))).thenReturn(accessTokenDO);

        // 调用，并断言
        assertPojoEquals(accessTokenDO, oauth2GrantService.grantImplicit(
                userId, userType, clientId, scopes));
    }

    @Test
    public void testGrantAuthorizationCodeForCode() {
        // 准备参数
        Long userId = randomLongId();
        Integer userType = randomEle(UserTypeEnum.values()).getValue();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        String redirectUri = randomString();
        String state = randomString();
        // mock 方法
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class);
        when(oauth2CodeService.createAuthorizationCode(eq(userId), eq(userType),
                eq(clientId), eq(scopes), eq(redirectUri), eq(state))).thenReturn(codeDO);

        // 调用，并断言
        assertEquals(codeDO.getCode(), oauth2GrantService.grantAuthorizationCodeForCode(userId, userType,
                clientId, scopes, redirectUri, state));
    }

    @Test
    public void testGrantAuthorizationCodeForCode_withPkceAndResource() {
        // 准备参数
        Long userId = randomLongId();
        Integer userType = randomEle(UserTypeEnum.values()).getValue();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        String redirectUri = randomString();
        String state = randomString();
        String codeChallenge = randomString();
        String resource = "http://127.0.0.1:48080/mcp";
        // mock 方法
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class);
        when(oauth2CodeService.createAuthorizationCode(eq(userId), eq(userType),
                eq(clientId), eq(scopes), eq(redirectUri), eq(state))).thenReturn(codeDO);

        // 调用，并断言
        assertEquals(codeDO.getCode(), oauth2GrantService.grantAuthorizationCodeForCode(userId, userType,
                clientId, scopes, redirectUri, state, codeChallenge, "S256", resource));
        verify(oauth2AuthorizationCodeExtraRedisDAO).set(org.mockito.ArgumentMatchers.argThat(extra ->
                codeDO.getCode().equals(extra.getCode())
                        && codeChallenge.equals(extra.getCodeChallenge())
                        && resource.equals(extra.getResource())));
    }

    @Test
    public void testGrantAuthorizationCodeForAccessToken() {
        // 准备参数
        String clientId = randomString();
        String code = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        String redirectUri = randomString();
        String state = randomString();
        Long tenantId = randomLongId();
        // mock 方法（code）
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class, o -> {
            o.setClientId(clientId);
            o.setRedirectUri(redirectUri);
            o.setState(state);
            o.setScopes(scopes);
            o.setTenantId(tenantId);
        });
        when(oauth2CodeService.consumeAuthorizationCode(eq(code))).thenReturn(codeDO);
        // mock 方法（创建令牌）
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.createAccessToken(eq(codeDO.getUserId()), eq(codeDO.getUserType()),
                eq(codeDO.getClientId()), eq(codeDO.getScopes()))).thenAnswer(invocation -> {
                    assertEquals(tenantId, TenantContextHolder.getTenantId());
                    return accessTokenDO;
                });

        // 调用，并断言
        assertPojoEquals(accessTokenDO, oauth2GrantService.grantAuthorizationCodeForAccessToken(
                clientId, code, redirectUri, state));
    }

    @Test
    public void testGrantAuthorizationCodeForAccessToken_withPkceAndResource() {
        // 准备参数
        String clientId = randomString();
        String code = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        String redirectUri = randomString();
        String state = randomString();
        String codeVerifier = "test-code-verifier";
        String resource = "http://127.0.0.1:48080/mcp";
        // mock 方法（code）
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class, o -> {
            o.setClientId(clientId);
            o.setRedirectUri(redirectUri);
            o.setState(state);
            o.setScopes(scopes);
        });
        when(oauth2CodeService.consumeAuthorizationCode(eq(code))).thenReturn(codeDO);
        OAuth2AuthorizationCodeExtraDO codeExtra = new OAuth2AuthorizationCodeExtraDO()
                .setCode(code)
                .setCodeChallenge(buildS256CodeChallenge(codeVerifier))
                .setCodeChallengeMethod("S256")
                .setResource(resource);
        when(oauth2AuthorizationCodeExtraRedisDAO.get(eq(code))).thenReturn(codeExtra);
        // mock 方法（创建令牌）
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.createAccessToken(eq(codeDO.getUserId()), eq(codeDO.getUserType()),
                eq(codeDO.getClientId()), eq(codeDO.getScopes()))).thenReturn(accessTokenDO);

        // 调用，并断言
        assertPojoEquals(accessTokenDO, oauth2GrantService.grantAuthorizationCodeForAccessToken(
                clientId, code, redirectUri, state, codeVerifier, resource, true));
        verify(oauth2AuthorizationCodeExtraRedisDAO).delete(code);
    }

    @Test
    public void testGrantAuthorizationCodeForAccessToken_codeVerifierMismatch() {
        // 准备参数
        String clientId = randomString();
        String code = randomString();
        String redirectUri = randomString();
        String state = randomString();
        // mock 方法（code）
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class, o -> {
            o.setClientId(clientId);
            o.setRedirectUri(redirectUri);
            o.setState(state);
        });
        when(oauth2CodeService.consumeAuthorizationCode(eq(code))).thenReturn(codeDO);
        OAuth2AuthorizationCodeExtraDO codeExtra = new OAuth2AuthorizationCodeExtraDO()
                .setCode(code)
                .setCodeChallenge(buildS256CodeChallenge("right-verifier"))
                .setCodeChallengeMethod("S256");
        when(oauth2AuthorizationCodeExtraRedisDAO.get(eq(code))).thenReturn(codeExtra);

        // 调用，并断言
        assertServiceException(() -> oauth2GrantService.grantAuthorizationCodeForAccessToken(
                clientId, code, redirectUri, state, "wrong-verifier", null, true),
                OAUTH2_GRANT_CODE_VERIFIER_MISMATCH);
    }

    @Test
    public void testGrantPassword() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        // mock 方法(认证)
        AdminUserDO user = randomPojo(AdminUserDO.class);
        when(adminAuthService.authenticate(eq(username), eq(password))).thenReturn(user);
        // mock 方法（访问令牌）
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.createAccessToken(eq(user.getId()), eq(UserTypeEnum.ADMIN.getValue()),
                eq(clientId), eq(scopes))).thenReturn(accessTokenDO);

        // 调用，并断言
        assertPojoEquals(accessTokenDO, oauth2GrantService.grantPassword(
                username, password, clientId, scopes));
    }

    @Test
    public void testGrantRefreshToken() {
        // 准备参数
        String refreshToken = randomString();
        String clientId = randomString();
        // mock 方法
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.refreshAccessToken(eq(refreshToken), eq(clientId)))
                .thenReturn(accessTokenDO);

        // 调用，并断言
        assertPojoEquals(accessTokenDO, oauth2GrantService.grantRefreshToken(
                refreshToken, clientId));
    }

    @Test
    public void testRevokeToken_clientIdError() {
        // 准备参数
        String clientId = randomString();
        String accessToken = randomString();
        // mock 方法
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.getAccessToken(eq(accessToken))).thenReturn(accessTokenDO);

        // 调用，并断言
        assertFalse(oauth2GrantService.revokeToken(clientId, accessToken));
    }

    @Test
    public void testRevokeToken_success() {
        // 准备参数
        String clientId = randomString();
        String accessToken = randomString();
        // mock 方法（访问令牌）
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class).setClientId(clientId);
        when(oauth2TokenService.getAccessToken(eq(accessToken))).thenReturn(accessTokenDO);
        // mock 方法（移除）
        when(oauth2TokenService.removeAccessToken(eq(accessToken))).thenReturn(accessTokenDO);

        // 调用，并断言
        assertTrue(oauth2GrantService.revokeToken(clientId, accessToken));
    }

    private static String buildS256CodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
