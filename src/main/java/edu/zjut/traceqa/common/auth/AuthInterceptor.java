package edu.zjut.traceqa.common.auth;

import jakarta.annotation.Resource;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证与权限拦截器。
 *
 * <p>拦截 {@code /api/**} 请求：</p>
 * <ol>
 *   <li>从 {@code Authorization: Bearer xxx} 提取令牌，解析失败抛 40101；</li>
 *   <li>校验令牌解析出的登录用户并写入 {@link UserContext}；</li>
 *   <li>若目标方法带有 {@link RequirePermission}，校验权限码，缺失抛 40300；</li>
 *   <li>请求结束清理线程上下文。</li>
 * </ol>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    @Resource
    private JwtService jwtService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        try {
            // 1. 解析并校验令牌
            UserContext.LoginUser loginUser = resolveLoginUser(request);

            // 2. 写入线程上下文
            UserContext.set(loginUser);

            // 3. 校验方法级权限
            RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
            if (requirePermission != null && !UserContext.hasPermission(requirePermission.value())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return true;
        } catch (Exception e) {
            // preHandle 抛异常时 Spring 不会回调本拦截器的 afterCompletion，
            // 必须在此处清理线程上下文，避免线程池复用导致用户串号
            UserContext.clear();
            throw e;
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        // 请求结束必须清理，防止线程池复用导致用户串号
        UserContext.clear();
    }

    /** 从请求头提取并解析登录用户 */
    private UserContext.LoginUser resolveLoginUser(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = header.substring(BEARER_PREFIX.length());
        return jwtService.parseToken(token);
    }
}