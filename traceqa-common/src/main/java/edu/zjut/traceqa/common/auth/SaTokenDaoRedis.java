package edu.zjut.traceqa.common.auth;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * sa-token 基于 Redis 的持久化 Dao（用现有 StringRedisTemplate 实现）。
 *
 * <p>token / 登录态等元数据以 String 存储；会话对象（SaSession）采用 JDK 序列化
 * 后 Base64 落 Redis。网关与各业务服务共享同一套 Redis，实现登录态跨服务生效。
 * Redis 不可用时自动降级（不抛异常），保证登录链路可用。</p>
 */
@AllArgsConstructor
public class SaTokenDaoRedis implements SaTokenDao {

    private static final Logger log = LoggerFactory.getLogger(SaTokenDaoRedis.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.debug("sa-token get 失败（降级）：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void set(String key, String value, long timeout) {
        try {
            if (timeout == NEVER_EXPIRE) {
                stringRedisTemplate.opsForValue().set(key, value);
            } else if (timeout > 0) {
                stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeout));
            }
        } catch (Exception e) {
            log.debug("sa-token set 失败（降级）：key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public void update(String key, String value) {
        set(key, value, NEVER_EXPIRE);
    }

    @Override
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("sa-token delete 失败：key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public long getTimeout(String key) {
        try {
            Long ttl = stringRedisTemplate.getExpire(key);
            if (ttl == null) {
                return NOT_VALUE_EXPIRE;
            }
            return ttl < 0 ? NEVER_EXPIRE : ttl;
        } catch (Exception e) {
            log.debug("sa-token getTimeout 失败：key={}, err={}", key, e.getMessage());
            return NOT_VALUE_EXPIRE;
        }
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        try {
            stringRedisTemplate.expire(key, Duration.ofSeconds(timeout));
        } catch (Exception e) {
            log.debug("sa-token updateTimeout 失败：key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public Object getObject(String key) {
        try {
            String raw = get(key);
            if (raw == null) {
                return null;
            }
            return deserialize(raw);
        } catch (Exception e) {
            log.debug("sa-token getObject 反序列化失败：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public <T> T getObject(String key, Class<T> classType) {
        Object value = getObject(key);
        return classType.cast(value);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        try {
            set(key, serialize(object), timeout);
        } catch (Exception e) {
            log.debug("sa-token setObject 序列化失败：key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public void updateObject(String key, Object object) {
        setObject(key, object, NEVER_EXPIRE);
    }

    @Override
    public void deleteObject(String key) {
        delete(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    @Override
    public SaSession getSession(String sessionId) {
        Object value = getObject(sessionId);
        return value instanceof SaSession session ? session : null;
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        setObject(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        updateObject(session.getId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        deleteObject(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return getObjectTimeout(sessionId);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        updateObjectTimeout(sessionId, timeout);
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        return new ArrayList<>();
    }

    /**
     * JDK 序列化 -> Base64
     */
    private String serialize(Object object) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    /**
     * Base64 -> JDK 反序列化
     */
    private Object deserialize(String text) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(text);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }
}