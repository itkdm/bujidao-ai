package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力执行确认状态
 *
 * @author bujidao
 */
public enum CapabilityConfirmationStatus {

    /** 当前能力不需要执行前确认 */
    NOT_REQUIRED,

    /** 已创建确认挑战，等待调用方确认 */
    CHALLENGE_CREATED,

    /** 确认令牌校验通过并已消费 */
    TOKEN_VALID,

    /** 确认令牌无效、过期或与当前请求不匹配 */
    TOKEN_INVALID,

    /** 确认基础设施执行失败 */
    ERROR

}
