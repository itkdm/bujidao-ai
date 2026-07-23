package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力执行状态
 *
 * @author bujidao
 */
public enum CapabilityStatus {

    /** 能力执行成功 */
    SUCCESS,

    /** 能力需要完成用户确认后才能继续执行 */
    CONFIRM_REQUIRED,

    /** 能力执行失败 */
    FAILURE,

    /** 能力被权限或治理策略拒绝 */
    DENIED

}
