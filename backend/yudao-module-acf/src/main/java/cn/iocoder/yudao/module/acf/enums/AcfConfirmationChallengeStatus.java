package cn.iocoder.yudao.module.acf.enums;

/**
 * ACF 确认挑战状态
 *
 * @author bujidao
 */
public enum AcfConfirmationChallengeStatus {

    /**
     * 等待用户确认
     */
    PENDING,
    /**
     * 已确认，令牌可用于一次原请求重试
     */
    CONFIRMED,
    /**
     * 确认令牌已被消费
     */
    USED,
    /**
     * 已过期
     */
    EXPIRED

}
