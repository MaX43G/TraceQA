package edu.zjut.traceqa.qaservice.service;

import edu.zjut.traceqa.qaservice.config.QaProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * LLM 调用熔断服务（Redis 持久化状态机）。
 *
 * <p>状态机 {@code CLOSED -> OPEN -> HALF_OPEN -> CLOSED}，状态存于共享 Redis，
 * 支持多实例/重启后延续。Redis 不可用时自动降级为放行（不熔断）。</p>
 */
@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    private static final String CB_KEY = "cb:llm";
    private static final String F_FAILURES = "failures";
    private static final String F_OPENED_AT = "openedAt";
    private static final String F_HALF_OPEN = "halfOpen";

    private final QaProperties properties;
    private final StringRedisTemplate stringRedisTemplate;

    private int failureThreshold;
    private long openMillis;
    private int halfOpenMaxCalls;

    public CircuitBreakerService(QaProperties properties, StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 初始化熔断参数
     */
    @PostConstruct
    public void init() {
        QaProperties.CircuitBreaker cb = properties.getCircuitBreaker();
        this.failureThreshold = cb.getFailureThreshold();
        this.openMillis = cb.getOpenMillis();
        this.halfOpenMaxCalls = cb.getHalfOpenMaxCalls();
    }

    /**
     * 是否允许发起 LLM 请求
     */
    public boolean allowRequest() {
        try {
            if (currentState() == State.HALF_OPEN) {
                return halfOpenIncrement() <= halfOpenMaxCalls;
            }
            return currentState() == State.CLOSED;
        } catch (Exception e) {
            log.debug("熔断判断降级（Redis 不可用）：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 记录一次成功，重置计数
     */
    public void recordSuccess() {
        try {
            stringRedisTemplate.opsForHash().put(CB_KEY, F_FAILURES, "0");
            stringRedisTemplate.opsForHash().put(CB_KEY, F_OPENED_AT, "0");
            stringRedisTemplate.opsForHash().put(CB_KEY, F_HALF_OPEN, "0");
        } catch (Exception e) {
            log.debug("熔断成功记录降级：{}", e.getMessage());
        }
    }

    /**
     * 记录一次失败，达到阈值则打开熔断
     */
    public void recordFailure() {
        try {
            if (currentState() != State.CLOSED) {
                return;
            }
            Long failures = stringRedisTemplate.opsForHash().increment(CB_KEY, F_FAILURES, 1);
            if (failures != null && failures >= failureThreshold) {
                stringRedisTemplate.opsForHash().put(CB_KEY, F_OPENED_AT, String.valueOf(System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.debug("熔断失败记录降级：{}", e.getMessage());
        }
    }

    /**
     * 当前熔断状态
     */
    public State currentState() {
        try {
            long openedAt = longOf(stringRedisTemplate.opsForHash().get(CB_KEY, F_OPENED_AT));
            if (openedAt == 0) {
                return State.CLOSED;
            }
            if (System.currentTimeMillis() - openedAt >= openMillis) {
                return State.HALF_OPEN;
            }
            return State.OPEN;
        } catch (Exception e) {
            return State.CLOSED;
        }
    }

    private long halfOpenIncrement() {
        Long value = stringRedisTemplate.opsForHash().increment(CB_KEY, F_HALF_OPEN, 1);
        return value == null ? 0 : value;
    }

    private long longOf(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 熔断状态枚举
     */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }
}