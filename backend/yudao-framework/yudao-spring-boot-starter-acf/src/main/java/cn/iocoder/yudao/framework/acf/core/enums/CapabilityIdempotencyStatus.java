package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力幂等获取结果
 *
 * @author bujidao
 */
public enum CapabilityIdempotencyStatus {

    /** 当前请求首次获得执行权 */
    ACQUIRED,

    /** 相同请求已经完成，直接返回历史结果 */
    REPLAYED,

    /** 幂等键已被执行中请求或不同请求占用 */
    CONFLICT,

    /** 幂等基础设施不可用或执行检查失败 */
    ERROR

}
