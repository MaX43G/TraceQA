package edu.zjut.traceqa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 缓存服务（String + JSON）。
 *
 * <p>统一封装查询结果缓存、Agent 决策缓存等读写。Redis 不可用时自动降级
 * （读返回空、写忽略），保证不影响主业务流程。</p>
 */
@Slf4j
@Component
public class RedisCacheService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /** 写入缓存（带 TTL） */
    public void put(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.debug("Redis 写入失败：key={}, err={}", key, e.getMessage());
        }
    }

    /** 读取缓存（简单类型） */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                MonitorService.recordCacheMiss();
                return Optional.empty();
            }
            MonitorService.recordCacheHit();
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.debug("Redis 读取失败：key={}, err={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 读取缓存（泛型，如 List） */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                MonitorService.recordCacheMiss();
                return Optional.empty();
            }
            MonitorService.recordCacheHit();
            return Optional.ofNullable(objectMapper.readValue(json, typeRef));
        } catch (Exception e) {
            log.debug("Redis 读取失败：key={}, err={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 删除缓存 */
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Redis 删除失败：key={}, err={}", key, e.getMessage());
        }
    }
}
