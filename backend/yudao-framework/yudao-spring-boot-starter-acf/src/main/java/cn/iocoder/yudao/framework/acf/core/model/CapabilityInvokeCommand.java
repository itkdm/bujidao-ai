package cn.iocoder.yudao.framework.acf.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 能力调用命令
 *
 * @author bujidao
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityInvokeCommand {

    /** 能力唯一名称 */
    private String name;

    /** 原始调用参数，由执行器按能力声明的参数类型完成转换 */
    private Object arguments;

    /** 调用治理上下文 */
    private CapabilityContext context;

    /** 执行需要确认的能力时，由确认流程签发的令牌 */
    private String confirmationToken;

    /** 调用方为重复请求提供的稳定幂等键 */
    private String idempotencyKey;

}
