package edu.zjut.traceqa.common.context;

import lombok.NoArgsConstructor;

/**
 * 微服务间透传的用户上下文请求头常量。
 *
 * <p>网关完成 Sa-Token 鉴权后，将解析出的登录用户信息写入以下请求头转发给下游微服务；
 * 下游服务通过 {@link UserContext} 读取，实现无状态、去会话化的用户上下文传递。</p>
 */
@NoArgsConstructor
public final class AuthHeaders {

    /**
     * 链路追踪 ID（网关生成或透传上游）
     */
    public static final String TRACE_ID = "X-Trace-Id";
    /**
     * 登录用户 ID
     */
    public static final String USER_ID = "X-User-Id";
    /**
     * 登录用户名
     */
    public static final String USERNAME = "X-Username";
    /**
     * 角色编码（如 ADMIN/USER）
     */
    public static final String ROLE = "X-User-Role";
    /**
     * 权限码集合（逗号分隔）
     */
    public static final String PERMISSIONS = "X-User-Permissions";
}