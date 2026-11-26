package cn.iocoder.yudao.module.mcp.framework.oauth2.cimd;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SSRF 地址族检查：拉取层与文档静态校验层共用，同一份逻辑，不复制两份。
 *
 * <p>判定流程：host 先按数字型 IPv4 写法归一化（十进制 / 十六进制整型、1-4 段点分写法，
 * 如 {@code 2130706433}、{@code 0x7f000001}、{@code 0x7f.0.0.1}），命中则直接判地址族，
 * 不走 DNS；否则 DNS 解析后逐个地址判定，任一地址非公开即拒绝。
 *
 * <p>谓词覆盖的地址族清单（IPv4 显式段 + JDK 谓词兜底的 IPv6 族）：
 * <ul>
 *   <li>回环：127.0.0.0/8、::1；</li>
 *   <li>内网：10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、fc00::/7；</li>
 *   <li>链路本地：169.254.0.0/16、fe80::/10；</li>
 *   <li>组播：224.0.0.0/4、ff00::/8；</li>
 *   <li>未指定：0.0.0.0/8、::；</li>
 *   <li>显式补段：100.64.0.0/10（CGNAT）、192.0.0.0/24（IETF 协议分配）、
 *       198.18.0.0/15（基准测试）、240.0.0.0/4（保留地址，含 255.255.255.255）；</li>
 *   <li>IPv4 映射 / 兼容 IPv6 地址（::ffff:a.b.c.d、::a.b.c.d）先归一化成 IPv4 再按上表判定，
 *       不能只看外层是不是 IPv6 就放行。</li>
 * </ul>
 *
 * <p>注意：本检查只拦“解析结果已知是非公开地址”。解析与建连之间的 DNS 变化
 * （DNS rebinding / TOCTOU）不在本层解决，见 docs/cimd-block-a-decisions.md。
 *
 * @author bujidao
 */
public final class McpCimdSsrfGuard {

    private McpCimdSsrfGuard() {
    }

    /**
     * 检查 host 解析出的地址是否全部公开。
     *
     * @param host            待检查的 host（字面 IP 或主机名）
     * @param dnsFailureError host 无法解析时用的错误类别：拉取层传 FETCH_FAILED，
     *                        文档静态校验层传 INVALID_DOCUMENT（fail-closed）
     * @throws McpCimdException 任一地址非公开时为 SSRF_BLOCKED；解析失败时为 dnsFailureError
     */
    public static void checkHostIsPublic(String host, McpCimdError dnsFailureError) throws McpCimdException {
        if (host == null || host.isBlank()) {
            throw new McpCimdException(McpCimdError.SSRF_BLOCKED, "host is blank");
        }
        InetAddress numeric = parseNumericIpv4(host);
        if (numeric != null) {
            // 数字型 IP 不走 DNS，直接判地址族
            if (isNonPublicAddress(numeric)) {
                throw new McpCimdException(McpCimdError.SSRF_BLOCKED,
                        "resolved address is not a public address");
            }
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new McpCimdException(dnsFailureError, "failed to resolve host", exception);
        }
        for (InetAddress address : addresses) {
            if (isNonPublicAddress(address)) {
                throw new McpCimdException(McpCimdError.SSRF_BLOCKED,
                        "resolved address is not a public address");
            }
        }
    }

    /**
     * 单个地址是否非公开。未知地址族 fail-closed，判为非公开。
     */
    public static boolean isNonPublicAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isNonPublicIpv4(bytes);
        }
        if (bytes.length == 16) {
            byte[] ipv4 = unwrapIpv4(bytes);
            if (ipv4 != null) {
                return isNonPublicIpv4(ipv4);
            }
            // 原生 IPv6：JDK 谓词覆盖回环 / 内网 / 链路本地 / 组播 / 未指定
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isMulticastAddress()
                    || address.isAnyLocalAddress();
        }
        return true;
    }

    /**
     * host 是否为 127.0.0.0/8 的数字型 IPv4 写法（含 2130706433、0x7f000001 这类整型写法），
     * 供 redirect_uri 的 loopback 判断复用。
     */
    public static boolean isLoopbackIpv4Literal(String host) {
        InetAddress numeric = parseNumericIpv4(host);
        return numeric != null && (numeric.getAddress()[0] & 0xFF) == 127;
    }

    /** IPv4 显式段判定：不依赖 JDK isSiteLocalAddress 的版本差异，全部按段列出来。 */
    private static boolean isNonPublicIpv4(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        if (b0 == 127) {
            return true; // 127.0.0.0/8 回环
        }
        if (b0 == 10) {
            return true; // 10.0.0.0/8
        }
        if (b0 == 172 && (b1 & 0xF0) == 16) {
            return true; // 172.16.0.0/12
        }
        if (b0 == 192 && b1 == 168) {
            return true; // 192.168.0.0/16
        }
        if (b0 == 169 && b1 == 254) {
            return true; // 169.254.0.0/16 链路本地（含云厂商元数据地址）
        }
        if (b0 == 100 && (b1 & 0xC0) == 64) {
            return true; // 100.64.0.0/10 CGNAT
        }
        if (b0 == 192 && b1 == 0 && b2 == 0) {
            return true; // 192.0.0.0/24 IETF 协议分配
        }
        if (b0 == 198 && (b1 & 0xFE) == 18) {
            return true; // 198.18.0.0/15 基准测试
        }
        if ((b0 & 0xF0) == 240) {
            return true; // 240.0.0.0/4 保留地址（含 255.255.255.255）
        }
        if ((b0 & 0xF0) == 224) {
            return true; // 224.0.0.0/4 组播
        }
        if (b0 == 0) {
            return true; // 0.0.0.0/8 未指定 / 本网络
        }
        return false;
    }

    /**
     * 16 字节 IPv6 地址里剥出内嵌 IPv4：::ffff:a.b.c.d（映射）或 ::a.b.c.d（兼容，已废弃）。
     * 不是内嵌形式返回 null。::1 也会落进兼容分支，剥出 0.0.0.1 后仍判为非公开，结果一致。
     */
    private static byte[] unwrapIpv4(byte[] bytes) {
        boolean mapped = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                mapped = false;
                break;
            }
        }
        if (mapped && bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) {
            return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
        }
        boolean compatible = true;
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) {
                compatible = false;
                break;
            }
        }
        if (compatible) {
            return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
        }
        return null;
    }

    /**
     * 把数字型 IPv4 写法解析成地址，解析不了返回 null（调用方走 DNS 路径）。
     * 支持 inet_addr 语义：单段整型（十进制或 0x 十六进制）、1-4 段点分写法，
     * 每段可为十进制、0x 十六进制或前导 0 八进制。
     */
    public static InetAddress parseNumericIpv4(String host) {
        if (host == null) {
            return null;
        }
        String text = host.trim();
        if (text.startsWith("[") && text.endsWith("]") && text.length() > 2) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.isEmpty() || text.indexOf(':') >= 0) {
            return null;
        }
        String[] parts = text.split("\\.", -1);
        if (parts.length < 1 || parts.length > 4) {
            return null;
        }
        long[] values = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Long parsed = parseNumericPart(parts[i]);
            if (parsed == null) {
                return null;
            }
            values[i] = parsed;
        }
        long address;
        switch (parts.length) {
            case 1:
                if (values[0] > 0xFFFFFFFFL) {
                    return null;
                }
                address = values[0];
                break;
            case 2:
                if (values[0] > 0xFFL || values[1] > 0xFFFFFFL) {
                    return null;
                }
                address = (values[0] << 24) | values[1];
                break;
            case 3:
                if (values[0] > 0xFFL || values[1] > 0xFFL || values[2] > 0xFFFFL) {
                    return null;
                }
                address = (values[0] << 24) | (values[1] << 16) | values[2];
                break;
            default:
                for (long value : values) {
                    if (value > 0xFFL) {
                        return null;
                    }
                }
                address = (values[0] << 24) | (values[1] << 16) | (values[2] << 8) | values[3];
                break;
        }
        byte[] bytes = new byte[]{
                (byte) (address >> 24), (byte) (address >> 16), (byte) (address >> 8), (byte) address};
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    /** 解析一段数字：0x 前缀为十六进制，前导 0 为八进制，否则十进制；不是纯数字返回 null。 */
    private static Long parseNumericPart(String part) {
        if (part == null || part.isEmpty()) {
            return null;
        }
        try {
            if (part.startsWith("0x") || part.startsWith("0X")) {
                if (part.length() == 2) {
                    return null;
                }
                return Long.parseLong(part.substring(2), 16);
            }
            if (part.length() > 1 && part.charAt(0) == '0') {
                return Long.parseLong(part, 8);
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return null;
                }
            }
            return Long.parseLong(part, 10);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

}
