package edu.zjut.traceqa.adminservice.config;

import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos WebUI 会话存储（内存态）。
 *
 * <p>管理员获取会话时签发短期令牌，用于 {@link NacosWebuiProxyFilter} 反向代理鉴权。</p>
 */
@Component
public class NacosWebuiSessionStore {

    @Resource
    private AdminProperties adminProperties;

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    /**
     * 创建令牌并返回
     */
    public String create() {
        String token = UUID.randomUUID().toString().replace("-", "");
        long ttlMillis = adminProperties.getNacos().getCookieExpireHours() * 60L * 60 * 1000;
        tokens.put(token, System.currentTimeMillis() + ttlMillis);
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
