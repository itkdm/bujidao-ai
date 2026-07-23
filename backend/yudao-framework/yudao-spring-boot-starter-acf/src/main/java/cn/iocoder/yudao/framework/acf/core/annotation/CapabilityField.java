package cn.iocoder.yudao.framework.acf.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 补充能力输入、输出字段的 Schema 元数据
 *
 * @author bujidao
 */
@Documented
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CapabilityField {

    /** 字段描述 */
    String description() default "";

    /** 字段示例值 */
    String example() default "";

    /** 是否包含敏感信息 */
    boolean sensitive() default false;

}
