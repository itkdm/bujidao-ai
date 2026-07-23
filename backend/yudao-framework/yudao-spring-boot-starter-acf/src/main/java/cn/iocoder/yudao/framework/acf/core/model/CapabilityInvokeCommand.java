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

}
