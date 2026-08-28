package edu.zjut.traceqa.common.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import edu.zjut.traceqa.config.AppProperties;
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
 * 可观测性端点 {@code /actuator/**} 仅管理员可访问，其中 {@code /actuator/prometheus}
 * 允许携带合法抓取令牌（供 Prometheus 拉取指标）。
 * 每次鉴权额外校验用户是否被禁用（禁用即强制注销）。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Prometheus 抓取令牌请求头 */
    private static final String SCRAPE_TOKEN_HEADER = "X-Scrape-Token";

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AppProperties appProperties;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
        // sa-token 拦截器：路由规则强制登录 + 自动解析 @SaCheckXxx 注解
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 业务 API：除放行项外需登录
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/login", "/api/auth/register", "/api/health",
                            "/api/announcement/active")
                    .check(r -> {
                        StpUtil.checkLogin();
                        checkUserEnabled();
                    });
            // Prometheus 抓取端点：携带合法抓取令牌或管理员身份放行
            SaRouter.match("/actuator/prometheus")
                    .check(r -> {
                        if (hasScrapeToken()) {
                            SaRouter.stop();
                        }
                        StpUtil.checkLogin();
                        StpUtil.checkRole("ADMIN");
                    });
            // 其余可观测性端点：仅管理员
            SaRouter.match("/actuator/**")
                    .check(r -> {
                        StpUtil.checkLogin();
                        StpUtil.checkRole("ADMIN");
                    });
        }))
                .addPathPatterns("/api/**", "/actuator/**");
    }

    /** 请求是否携带与配置一致的 Prometheus 抓取令牌 */
    private boolean hasScrapeToken() {
        String expected = appProperties.getObservability().getScrapeToken();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String provided = SaHolder.getRequest().getHeader(SCRAPE_TOKEN_HEADER);
        return expected.equals(provided);
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
