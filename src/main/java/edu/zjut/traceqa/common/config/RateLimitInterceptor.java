package edu.zjut.traceqa.common.config;

import edu.zjut.traceqa.common.auth.UserContext;
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
import java.util.UUID;

/**
 * 接口限流拦截器（Redis 滑动窗口，细粒度）。
 *
 * <p>按「路径 + 维度（IP / 登录用户）」计数，支持每分钟阈值。
 * <ul>
 *   <li><b>登录/注册</b>：按 IP 限制（防爆破的账号级锁定见 {@link edu.zjut.traceqa.service.AuthService}）；</li>
 *   <li><b>问答流式接口</b>：按 IP + 登录用户双重限制（防 LLM 滥用）；</li>
 *   <li>其余 /api/**：默认按 IP 限制。</li>
 * </ul>
 * Redis 不可用时放行（降级），返回统一错误结构 {@code {code:40003}}。</p>
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 限流规则：perIpMin / perUserMin 为每分钟次数 */
    private record Rule(int perIpMin, int perUserMin) {
        Rule(int perIpMin) { this(perIpMin, 0); }
    }

    /** 各接口限流规则 */
    private static final Map<String, Rule> RULES = Map.of(
            "/api/auth/login", new Rule(10),
            "/api/auth/register", new Rule(10),
            "/api/chat/stream", new Rule(120, 40)
    );

    /** 未匹配到专用规则的 /api/** 默认按 IP 限制 */
    private static final Rule DEFAULT_RULE = new Rule(240);

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String uri = request.getRequestURI();
        Rule rule = RULES.getOrDefault(uri, DEFAULT_RULE);
        String ip = resolveIp(request);
        String userKey = userKey();

        try {
            // 1. 按 IP 限制
            if (rule.perIpMin() > 0 && limited("ip", uri, ip, rule.perIpMin())) {
                return block(response);
            }
            // 2. 登录用户维度限制（防单个用户滥用 LLM）
            if (rule.perUserMin() > 0 && userKey != null
                    && limited("user", uri, userKey, rule.perUserMin())) {
                return block(response);
            }
        } catch (Exception e) {
            // Redis 不可用：放行，避免误伤
            log.debug("限流检查降级（Redis 不可用）：{}", e.getMessage());
        }
        return true;
    }

    /** 滑动窗口计数：返回是否超限 */
    private boolean limited(String scope, String path, String key, int limit) {
        String rkey = "rl:" + scope + ":" + path + ":" + key;
        long now = System.currentTimeMillis();
        long cutoff = now - RateLimitInterceptor.WINDOW.toMillis();
        stringRedisTemplate.opsForZSet().removeRangeByScore(rkey, 0, cutoff);
        Long count = stringRedisTemplate.opsForZSet().zCard(rkey);
        if (count != null && count >= limit) {
            return true;
        }
        stringRedisTemplate.opsForZSet().add(rkey, UUID.randomUUID().toString(), (double) now);
        stringRedisTemplate.expire(rkey, RateLimitInterceptor.WINDOW.plus(Duration.ofSeconds(5)));
        return false;
    }

    /** 返回 429 统一错误结构 */
    private boolean block(HttpServletResponse response) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40003,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}");
        return false;
    }

    /** 已登录用户的 userKey（未登录返回 null，仅按 IP 限制） */
    private String userKey() {
        Long uid = UserContext.getUserId();
        return uid == null ? null : String.valueOf(uid);
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