package edu.zjut.traceqa.common.auth;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户的线程上下文。
 *
 * <p>基于 sa-token 会话：登录时把 {@link LoginUser} 写入会话，
 * 各 Service/Controller 通过 {@link #get()} 读取当前用户，
 * 避免在方法签名中层层透传用户参数。</p>
 */
public final class UserContext {

    /** 会话中存储登录用户信息的 key */
    public static final String SESSION_KEY = "loginUser";

    private UserContext() {
    }

    /** 读取当前登录用户（未登录返回 null） */
    public static LoginUser get() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object user = StpUtil.getSession().get(SESSION_KEY);
            return user instanceof LoginUser lu ? lu : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取当前用户 ID，未登录返回 null */
    public static Long getUserId() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断当前用户是否拥有指定权限码。
     * 角色为 ADMIN 或含 "all" 通配权限时视为拥有全部权限。
     */
    public static boolean hasPermission(String permission) {
        LoginUser user = get();
        if (user == null || user.getPermissions() == null) {
            return false;
        }
        // 管理员角色始终拥有全部权限（即使角色权限配置被误改）
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            return true;
        }
        return user.getPermissions().contains("all") || user.getPermissions().contains(permission);
    }

    /**
     * 登录用户信息快照（写入 sa-token 会话的轻量对象）。
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
