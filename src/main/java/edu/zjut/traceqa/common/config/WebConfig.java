package edu.zjut.traceqa.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
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
 * 前端跨域由 Nuxt 代理解决，因此不在此处额外开放 CORS。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
        // sa-token 拦截器：路由规则强制登录 + 自动解析 @SaCheckXxx 注解
        // 拦截器已限定 /api/**，此处 match("/**") 匹配进入的全部请求，排除放行路径
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                        .match("/**")
                        .notMatch("/api/auth/login", "/api/auth/register", "/api/health")
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/api/**");
    }
}
