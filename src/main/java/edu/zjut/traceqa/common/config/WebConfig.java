package edu.zjut.traceqa.common.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import edu.zjut.traceqa.model.po.User;
import edu.zjut.traceqa.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>注册 sa-token 拦截器（路由鉴权 + {@code @SaCheckLogin/@SaCheckRole} 注解鉴权）：
 * 拦截 {@code /api/**}，放行登录、注册、健康检查；限流拦截器保护登录/注册/问答接口。
 * 每次鉴权额外校验用户是否被禁用（禁用即强制注销）。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Resource
    private UserMapper userMapper;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
        // sa-token 拦截器：路由规则强制登录 + 自动解析 @SaCheckXxx 注解
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                        .match("/**")
                        .notMatch("/api/auth/login", "/api/auth/register", "/api/health")
                        .check(r -> {
                            StpUtil.checkLogin();
                            checkUserEnabled();
                        })))
                .addPathPatterns("/api/**");
    }

    /** 校验当前登录用户是否被禁用；被禁用则强制注销并视为未登录 */
    private void checkUserEnabled() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                StpUtil.logout();
                throw new NotLoginException("该账号已被禁用", "not-login", "token");
            }
        } catch (NotLoginException e) {
            throw e;
        } catch (Exception e) {
            // 数据库异常等：不阻断（降级为仅 token 校验）
        }
    }
}
