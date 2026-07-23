package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力权限校验模式
 * @author bujidao
 */
public enum CapabilityPermissionMode {

    /** 拥有任意一个权限即可调用 */
    ANY,

    /** 必须拥有全部权限才可调用 */
    ALL

}
