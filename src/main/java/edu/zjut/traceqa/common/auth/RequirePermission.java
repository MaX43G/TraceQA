package edu.zjut.traceqa.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解（RBAC）。
 *
 * <p>标注在 Controller 方法上，由 {@link AuthInterceptor} 在执行前校验当前用户
 * 是否拥有指定权限码。未登录抛 {@code 40100}，无权限抛 {@code 40300}。</p>
 *
 * <pre>{@code
 * @RequirePermission("kb:manage")
 * public ApiResponse<Void> create() { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 所需权限码 */
    String value();
}