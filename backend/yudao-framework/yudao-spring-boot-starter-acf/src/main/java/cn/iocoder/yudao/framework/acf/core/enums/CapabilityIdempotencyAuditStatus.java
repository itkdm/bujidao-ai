package cn.iocoder.yudao.framework.acf.core.enums;

/**
 * 能力幂等执行审计状态
 *
 * @author bujidao
 */
public enum CapabilityIdempotencyAuditStatus {

    /** 调用方未请求幂等控制 */
    NOT_REQUESTED,

    /** 当前请求取得执行权 */
    ACQUIRED,

    /** 返回已经完成的历史结果 */
    REPLAYED,

    /** 幂等键被执行中请求或不同请求占用 */
    CONFLICT,

    /** 执行结果已完成幂等收口 */
    COMPLETED,

    /** 目标执行失败并已通知幂等服务 */
    FAILED,

    /** Target started but its terminal result is not known yet. */
    UNCERTAIN,

    /** 目标执行前失败，已释放执行权 */
    RELEASED,

    /** 幂等基础设施执行失败 */
    ERROR

}
