package cn.iocoder.yudao.module.mcp.framework.oauth2.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2ClientDO;

import java.net.URI;
import java.util.Map;

/**
 * MCP OAuth resource 校验工具。
 *
 * @author bujidao
 */
public class McpOAuthResourceValidator {

    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK_IP = "127.0.0.1";

    /**
     * 校验请求带的 resource 是否与客户端注册 / 当前服务地址一致。
     * 未声明 resource（RFC 8707 里 resource 是可选参数）时本步不约束：
     * 真正的绑定在兑换步——{@code validateAuthorizationCodeBinding} 要求兑换的
     * resource 与授权时存的 codeExtra.resource 逐值相等，授权带 R、兑换
     * 不带仍判 invalid_grant，授权不带、兑换带 R 同样判 invalid_grant，
     * 带与不带的 resource 混不过去。
     */
    public static boolean matches(String resource, OAuth2ClientDO client, String currentResource) {
        if (StrUtil.isBlank(resource)) {
            return true;
        }
        String registeredResource = getAdditionalString(client, "mcp_resource", "mcpResource");
        if (matches(resource, registeredResource)) {
            return true;
        }
        return matches(resource, currentResource);
    }

    private static boolean matches(String resource, String expectedResource) {
        if (StrUtil.isBlank(expectedResource)) {
            return false;
        }
        return StrUtil.equals(resource, expectedResource) || isSameLocalLoopbackResource(resource, expectedResource);
    }

    private static boolean isSameLocalLoopbackResource(String resource, String expectedResource) {
        URI resourceUri = parseUri(resource);
        URI expectedUri = parseUri(expectedResource);
        if (resourceUri == null || expectedUri == null) {
            return false;
        }
        return StrUtil.equalsIgnoreCase(resourceUri.getScheme(), expectedUri.getScheme())
                && isLocalLoopbackHost(resourceUri.getHost())
                && isLocalLoopbackHost(expectedUri.getHost())
                && port(resourceUri) == port(expectedUri)
                && StrUtil.equals(StrUtil.blankToDefault(resourceUri.getPath(), "/"),
                        StrUtil.blankToDefault(expectedUri.getPath(), "/"))
                && StrUtil.equals(resourceUri.getQuery(), expectedUri.getQuery());
    }

    private static boolean isLocalLoopbackHost(String host) {
        return StrUtil.equalsIgnoreCase(host, LOCALHOST) || StrUtil.equals(host, LOOPBACK_IP);
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if (StrUtil.equalsIgnoreCase(uri.getScheme(), "http")) {
            return 80;
        }
        if (StrUtil.equalsIgnoreCase(uri.getScheme(), "https")) {
            return 443;
        }
        return -1;
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String getAdditionalString(OAuth2ClientDO client, String snakeKey, String camelKey) {
        if (client == null || StrUtil.isBlank(client.getAdditionalInformation())) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> additionalInformation = JsonUtils.parseObject(client.getAdditionalInformation(), Map.class);
            if (additionalInformation == null) {
                return null;
            }
            Object value = additionalInformation.get(snakeKey);
            if (value == null) {
                value = additionalInformation.get(camelKey);
            }
            return value == null ? null : value.toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

}
