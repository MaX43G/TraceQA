package edu.zjut.traceqa.common.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可观测性工具（Grafana / Prometheus）访问会话存储（内存版）。
 *
 * <p>管理员通过 {@code POST /api/monitor/observability/session} 获取一个短期
 * HttpOnly Cookie（{@code tq_obs}），{@link ObservabilityProxyFilter} 据此放行
 * 对 {@code /grafana/**} 与 {@code /prometheus/**} 的反向代理。会话有 TTL，到期自动失效。</p>
 */
@Component
public class ObservabilitySessionStore {

    /** 会话有效期（毫秒），默认 1 小时 */
    private static final long TTL_MILLIS = 60L * 60 * 1000;

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    /** 创建并返回一个会话令牌 */
    public String create() {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, System.currentTimeMillis() + TTL_MILLIS);
        return token;
    }

    /** 校验会话令牌是否有效（过期则移除） */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }
}