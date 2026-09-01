package edu.zjut.traceqa.qaservice.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * LLM 调用熔断服务（基于 Resilience4j）。
 *
 * <p>封装 Resilience4j 的 {@code CircuitBreaker}（实例名 {@code llm}），
 * 提供 {@code allowRequest/recordSuccess/recordFailure/currentState} 接口，
 * 供 {@link LlmService} 调用并暴露给监控。状态机由 Resilience4j 依据滑动窗口与失败率自动维护。</p>
 */
@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final CircuitBreaker breaker;

    /**
     * 从 Resilience4j 注册表获取名为 {@code llm} 的熔断器实例。
     *
     * @param registry Resilience4j 熔断器注册表（由 spring-boot3 自动装配）
     */
    public CircuitBreakerService(CircuitBreakerRegistry registry) {
        this.breaker = registry.circuitBreaker("llm");
    }

    /**
     * 是否允许发起 LLM 请求（尝试获取一次许可）。
     *
     * @return 允许返回 true；熔断打开返回 false
     */
    public boolean allowRequest() {
        return breaker.tryAcquirePermission();
    }

    /**
     * 记录一次调用成功。
     */
    public void recordSuccess() {
        try {
            breaker.onSuccess(0, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("熔断成功记录异常：{}", e.getMessage());
        }
    }

    /**
     * 记录一次调用失败。
     */
    public void recordFailure() {
        try {
            breaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("LLM call failed"));
        } catch (Exception e) {
            log.debug("熔断失败记录异常：{}", e.getMessage());
        }
    }

    /**
     * 当前熔断状态。
     *
     * @return CLOSED / OPEN / HALF_OPEN
     */
    public State currentState() {
        return switch (breaker.getState()) {
            case OPEN -> State.OPEN;
            case HALF_OPEN -> State.HALF_OPEN;
            default -> State.CLOSED;
        };
    }

    /**
     * 熔断状态枚举（与监控展示保持一致）。
     */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }
}