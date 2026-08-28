package edu.zjut.traceqa.service;

import edu.zjut.traceqa.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 熔断降级服务（状态存 Redis，重启/多实例共享）。
 *
 * <p>为 LLM 调用提供「熔断与降级」机制：连续失败达到阈值后熔断打开，
 * 一段时间内直接短路（跳过 LLM 调用，走纯检索降级链路）；熔断窗口过后进入
 * 半开状态，允许少量试探调用，成功则恢复。</p>
 *
 * <p>状态流转：{@code CLOSED} -> {@code OPEN} -> {@code HALF_OPEN} -> {@code CLOSED}。
 * Redis 不可用时降级为「不熔断」（允许调用），保证不误伤主链路。</p>
 */
@Slf4j
@Service
public class CircuitBreakerService {

    private static final String CB_KEY = "cb:llm";
    private static final String F_FAILURES = "failures";
    private static final String F_OPENED_AT = "openedAt";
    private static final String F_HALF_OPEN = "halfOpen";

    @Resource
    private AppProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private int failureThreshold;
    private long openMillis;
    private int halfOpenMaxCalls;

    /**
     * 熔断状态
     */
    public enum State {CLOSED, OPEN, HALF_OPEN}

    @PostConstruct
    private void init() {
        AppProperties.CircuitBreaker cfg = properties.getCircuitBreaker();
        this.failureThreshold = cfg.getFailureThreshold();
        this.openMillis = cfg.getOpenMillis();
        this.halfOpenMaxCalls = cfg.getHalfOpenMaxCalls();
    }

    /**
     * 判断当前是否允许发起 LLM 调用
     */
    public boolean allowRequest() {
        State state = currentState();
        // 半开状态：限制试探调用并发数
        if (state == State.HALF_OPEN) {
            return halfOpenIncrement() <= halfOpenMaxCalls;
        }
        return state == State.CLOSED;
    }

    /**
     * 记录一次 LLM 调用成功
     */
    public void recordSuccess() {
        try {
            HashOperations<String, Object, Object> ops = stringRedisTemplate.opsForHash();
            ops.put(CB_KEY, F_FAILURES, "0");
            ops.put(CB_KEY, F_HALF_OPEN, "0");
            ops.put(CB_KEY, F_OPENED_AT, "0");
        } catch (Exception e) {
            log.debug("熔断状态写入失败（忽略）：{}", e.getMessage());
        }
    }

    /**
     * 记录一次 LLM 调用失败，可能触发熔断
     */
    public void recordFailure() {
        try {
            HashOperations<String, Object, Object> ops = stringRedisTemplate.opsForHash();
            long failures = ops.increment(CB_KEY, F_FAILURES, 1);
            if (currentState() == State.CLOSED && failures >= failureThreshold) {
                ops.put(CB_KEY, F_OPENED_AT, String.valueOf(System.currentTimeMillis()));
                log.warn("LLM 连续失败 {} 次，熔断打开", failures);
            }
        } catch (Exception e) {
            log.debug("熔断状态写入失败（忽略）：{}", e.getMessage());
        }
    }

    /**
     * 计算当前熔断状态
     */
    public State currentState() {
        long opened = readOpenedAt();
        // 从未熔断
        if (opened == 0) {
            return State.CLOSED;
        }
        // 已过熔断窗口 -> 半开
        if (System.currentTimeMillis() - opened >= openMillis) {
            return State.HALF_OPEN;
        }
        return State.OPEN;
    }

    /**
     * 半开状态试探调用计数自增
     */
    private long halfOpenIncrement() {
        try {
            return stringRedisTemplate.opsForHash().increment(CB_KEY, F_HALF_OPEN, 1);
        } catch (Exception e) {
            log.debug("熔断计数读取失败（忽略）：{}", e.getMessage());
            return 1;
        }
    }

    /**
     * 读取熔断打开时间（Redis 不可用时视为未熔断）
     */
    private long readOpenedAt() {
        try {
            Object v = stringRedisTemplate.opsForHash().get(CB_KEY, F_OPENED_AT);
            return v == null ? 0L : Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            log.debug("熔断状态读取失败（忽略）：{}", e.getMessage());
            return 0L;
        }
    }
}
