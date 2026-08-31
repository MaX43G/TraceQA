package edu.zjut.traceqa.common.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级角色校验注解。
 *
 * <p>标注在 Controller/Service 方法上，要求当前登录用户具备指定角色编码，
 * 由 {@link RbacAspect} 基于网关透传的用户上下文完成校验。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 所需角色编码（如 ADMIN）
     */
    String value();
}