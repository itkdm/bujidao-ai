package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力风险等级
 * @author bujidao
 */
public enum CapabilityRiskLevel {

    /** 低风险，通常为只读操作 */
    LOW,

    /** 中风险，通常会产生可恢复的业务影响 */
    MEDIUM,

    /** 高风险，通常会产生重大或不可恢复的业务影响 */
    HIGH

}
