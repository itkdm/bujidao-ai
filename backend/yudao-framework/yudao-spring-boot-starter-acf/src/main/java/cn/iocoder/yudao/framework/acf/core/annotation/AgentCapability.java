package cn.iocoder.yudao.framework.acf.core.annotation;

import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明可供 Agent 调用的业务能力
 * @author bujidao
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentCapability {

    /** 能力唯一名称，使用“领域.资源.动作”格式，例如 erp.product.search */
    String name();

    /** 面向用户展示的能力标题 */
    String title();

    /** 帮助 Agent 理解能力用途的描述 */
    String description();

    /** 能力分类 */
    String category() default "";

    /** 调用能力所需的权限标识 */
    String[] permissions();

    /** 多个权限之间的校验模式 */
    CapabilityPermissionMode permissionMode() default CapabilityPermissionMode.ALL;

    /** 能力风险等级 */
    CapabilityRiskLevel riskLevel() default CapabilityRiskLevel.LOW;

    /** 执行能力是否会改变系统状态 */
    boolean sideEffect() default false;

    /** 执行前是否需要用户确认 */
    boolean confirmationRequired() default false;

    /** 能力版本 */
    String version() default "1.0.0";

    /** 最大执行时间，单位毫秒 */
    int timeoutMs() default 30_000;

}
