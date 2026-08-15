package edu.zjut.traceqa.common.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户的线程上下文。
 *
 * <p>由 {@link AuthInterceptor} 在 JWT 校验通过后写入，供各 Service/Controller
 * 通过 {@link #get()} 读取当前用户，避免在方法签名中层层透传用户参数。</p>
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    /** 写入当前登录用户 */
    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    /** 获取当前登录用户，未登录返回 null */
    public static LoginUser get() {
        return CURRENT.get();
    }

    /** 获取当前用户 ID，未登录返回 null */
    public static Long getUserId() {
        LoginUser user = CURRENT.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 判断当前用户是否拥有指定权限码。
     * 角色为 ADMIN 或含 "all" 通配权限时视为拥有全部权限。
     */
    public static boolean hasPermission(String permission) {
        LoginUser user = CURRENT.get();
        if (user == null || user.getPermissions() == null) {
            return false;
        }
        // 管理员角色始终拥有全部权限（即使角色权限配置被误改）
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            return true;
        }
        return user.getPermissions().contains("all") || user.getPermissions().contains(permission);
    }

    /** 清理当前线程上下文（请求结束必须调用） */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 登录用户信息快照（写入 JWT 与线程上下文的轻量对象）。
     */
    @Data
    @AllArgsConstructor
    public static class LoginUser {

        /** 用户 ID */
        private Long userId;
        /** 登录账号 */
        private String username;
        /** 昵称 */
        private String nickname;
        /** 角色编码（如 ADMIN/USER） */
        private String roleCode;
        /** 权限码集合 */
        private List<String> permissions;
    }
}