package edu.zjut.traceqa.common.config;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

/**
 * 接口限流拦截器（Redis 计数器，固定窗口）。
 *
 * <p>对登录、注册、问答等易被刷接口按 IP 限流；Redis 不可用时放行（降级）。
 * 返回统一错误结构 {@code {code:40003, msg:"请求过于频繁"}}。</p>
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 路径 -> 每分钟最大次数 */
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 10,
            "/api/auth/register", 10,
            "/api/chat/stream", 20
    );

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        Integer limit = LIMITS.get(request.getRequestURI());
        if (limit == null) {
            return true;
        }
        String key = "rl:" + request.getRequestURI() + ":" + resolveIp(request);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, WINDOW);
            }
            if (count != null && count > limit) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":40003,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return false;
            }
        } catch (Exception e) {
            // Redis 不可用：放行，避免误伤
            log.debug("限流检查降级（Redis 不可用）：{}", e.getMessage());
        }
        return true;
    }

    /** 解析客户端 IP（优先 X-Forwarded-For） */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
