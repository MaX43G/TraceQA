package edu.zjut.traceqa.common.rbac;

import edu.zjut.traceqa.common.context.CurrentUser;
import edu.zjut.traceqa.common.context.UserContext;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RBAC 注解切面。
 *
 * <p>基于网关透传的 {@link UserContext} 校验 {@code @RequirePermission} 与
 * {@code @RequireRole} 注解，无需访问数据库，实现下游服务的轻量二次鉴权。</p>
 */
@Aspect
@Component
public class RbacAspect {

    private static final Logger log = LoggerFactory.getLogger(RbacAspect.class);

    /**
     * 拦截带 @RequirePermission 的方法
     */
    @Before("@annotation(permission)")
    public void checkPermission(RequirePermission permission) {
        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.hasPermission(permission.value())) {
            log.warn("权限不足：userId={}, need={}", user.getUserId(), permission.value());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 拦截带 @RequireRole 的方法
     */
    @Before("@annotation(role)")
    public void checkRole(RequireRole role) {
        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.hasRole(role.value())) {
            log.warn("角色不足：userId={}, need={}", user.getUserId(), role.value());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}