package edu.zjut.traceqa.common.context;

import lombok.NoArgsConstructor;

/**
 * 当前登录用户的线程上下文。
 *
 * <p>在微服务架构中，登录态由网关统一校验，并将用户信息以请求头透传给下游服务。
 * 下游服务通过 {@link UserContextFilter} 解析请求头并写入本类的 {@link ThreadLocal}，
 * 各 Service/Controller 通过 {@link #get()} 读取当前用户，避免在方法签名中层层透传。</p>
 */
@NoArgsConstructor
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    /**
     * 读取当前登录用户（未登录或未透传返回 null）
     */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前用户 ID，未登录返回 null
     */
    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 设置当前登录用户（由 UserContextFilter 调用）
     */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /**
     * 清理当前用户（请求结束时调用，防止线程复用串号）
     */
    public static void clear() {
        HOLDER.remove();
    }
}