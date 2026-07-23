package cn.iocoder.yudao.framework.acf.core.standard;

import java.util.regex.Pattern;

/**
 * ACF 能力名称规范
 *
 * @author bujidao
 */
public final class AcfCapabilityNamingConvention {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){2,}$");

    private AcfCapabilityNamingConvention() {
    }

    /**
     * 判断能力名称是否符合“领域.资源.动作”格式
     *
     * @param capabilityName 能力名称
     * @return 是否符合命名规范
     */
    public static boolean isValid(String capabilityName) {
        return capabilityName != null && PATTERN.matcher(capabilityName).matches();
    }

}
