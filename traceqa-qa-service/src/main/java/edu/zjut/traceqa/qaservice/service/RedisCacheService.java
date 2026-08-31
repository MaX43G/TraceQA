package edu.zjut.traceqa.qaservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 缓存服务（字符串 + JSON）。
 *
 * <p>对检索/决策结果做短 TTL 缓存，Redis 不可用时自动降级（读返回空、写忽略）。</p>
 */
@Component
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 写入缓存
     */
    public void put(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.debug("缓存写入失败（降级）：key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 读取简单类型缓存
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(raw, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 读取泛型类型缓存
     */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(raw, typeRef));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}