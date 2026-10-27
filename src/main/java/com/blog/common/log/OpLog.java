package com.blog.common.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台操作日志注解：标注在管理接口方法上，由切面自动落库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /**
     * 业务模块，如 article / theme / comment
     */
    String module() default "";

    /**
     * 动作，如 create / update / delete / publish
     */
    String action() default "";
}
