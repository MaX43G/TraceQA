package edu.zjut.traceqa.adminservice.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LightRAG WebUI 会话存储（内存态）。
 *
 * <p>管理员获取会话时签发短期令牌，用于 {@code LightRagWebuiProxyFilter} 反向代理鉴权。</p>
 */
@Component
public class LightRagWebuiSessionStore {

    /**
     * 令牌有效期（24 小时）
     */
    private static final long TTL_MILLIS = 24L * 60 * 60 * 1000;

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    /**
     * 创建令牌并返回
     */
    public String create() {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, System.currentTimeMillis() + TTL_MILLIS);
        return token;
    }

    /**
     * 校验令牌是否有效
     */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expireAt = tokens.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            tokens.remove(token);
            return false;
        }
        return true;
    }
}