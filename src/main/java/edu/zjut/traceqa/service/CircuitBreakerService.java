package edu.zjut.traceqa.service;

import edu.zjut.traceqa.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 熔断降级服务。
 *
 * <p>为 LLM 调用提供「熔断与降级」机制：连续失败达到阈值后熔断打开，
 * 一段时间内直接短路（跳过 LLM 调用，走纯检索降级链路）；熔断窗口过后进入
 * 半开状态，允许少量试探调用，成功则恢复。</p>
 *
 * <p>状态流转：{@code CLOSED} -> {@code OPEN} -> {@code HALF_OPEN} -> {@code CLOSED}。</p>
 */
@Slf4j
@Service
public class CircuitBreakerService {

    @Resource
    private AppProperties properties;

    private int failureThreshold;
    private long openMillis;
    private int halfOpenMaxCalls;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);

    /** 熔断状态 */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    @PostConstruct
    private void init() {
        AppProperties.CircuitBreaker cfg = properties.getCircuitBreaker();
        this.failureThreshold = cfg.getFailureThreshold();
        this.openMillis = cfg.getOpenMillis();
        this.halfOpenMaxCalls = cfg.getHalfOpenMaxCalls();
    }

    /** 判断当前是否允许发起 LLM 调用 */
    public boolean allowRequest() {
        State state = currentState();
        // 半开状态：限制试探调用并发数
        if (state == State.HALF_OPEN) {
            return halfOpenCalls.incrementAndGet() <= halfOpenMaxCalls;
        }
        return state == State.CLOSED;
    }

    /** 记录一次 LLM 调用成功 */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        halfOpenCalls.set(0);
        openedAt.set(0);
    }

    /** 记录一次 LLM 调用失败，可能触发熔断 */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (currentState() == State.CLOSED && failures >= failureThreshold) {
            openedAt.set(System.currentTimeMillis());
            log.warn("LLM 连续失败 {} 次，熔断打开", failures);
        }
    }

    /** 计算当前熔断状态 */
    public State currentState() {
        long opened = openedAt.get();
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
}