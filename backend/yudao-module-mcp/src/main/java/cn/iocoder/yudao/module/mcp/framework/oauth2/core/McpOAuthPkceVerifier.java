package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * MCP OAuth PKCE 校验器。
 *
 * @author bujidao
 */
public class McpOAuthPkceVerifier {

    private static final String CODE_CHALLENGE_METHOD_S256 = "S256";

    public static void validateCodeChallenge(String codeChallenge, String codeChallengeMethod) {
        if (StrUtil.isBlank(codeChallenge)) {
            throw McpOAuthException.invalidRequest("code_challenge is required");
        }
        if (!StrUtil.equals(codeChallengeMethod, CODE_CHALLENGE_METHOD_S256)) {
            throw McpOAuthException.invalidRequest("code_challenge_method must be S256");
        }
    }

    public static void verify(String codeChallenge, String codeVerifier) {
        if (StrUtil.isBlank(codeVerifier)) {
            throw McpOAuthException.invalidGrant("code_verifier is required");
        }
        if (!StrUtil.equals(codeChallenge, buildS256CodeChallenge(codeVerifier))) {
            throw McpOAuthException.invalidGrant("code_verifier is invalid");
        }
    }

    private static String buildS256CodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

}
