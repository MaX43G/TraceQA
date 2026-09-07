package edu.zjut.traceqa.qaservice.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 事件发布器（含 Micrometer 可观测性指标）。
 *
 * <p>统一封装结构化的 {@code event + data} 写入，所有失败均记录并安全忽略，
 * 保证流式推送不因单次失败中断整体编排。</p>
 *
 * <p>指标（可通过 /actuator/prometheus 暴露）：</p>
 * <ul>
 *   <li>{@code sse.messages.sent} — 成功发送的 SSE 事件计数（按 event 标签）</li>
 *   <li>{@code sse.messages.failed} — 发送失败的 SSE 事件计数（按 event 标签）</li>
 *   <li>{@code sse.send.latency} — 单次 SSE 写入耗时 Timer</li>
 *   <li>{@code sse.connections.active} — 当前活跃 SSE 连接数 Gauge</li>
 * </ul>
 */
@Component
public class SsePublisher {

    private static final Logger log = LoggerFactory.getLogger(SsePublisher.class);

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MeterRegistry meterRegistry;

    private Counter sentCounter;
    private Counter failedCounter;
    private Timer sendTimer;
    private final AtomicInteger activeConnections = new AtomicInteger();
    /** 跟踪每个 emitter 的活跃状态，避免重复计数 */
    private final ConcurrentMap<SseEmitter, Boolean> trackedEmitters = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        sentCounter = Counter.builder("sse.messages.sent")
                .description("Total SSE messages sent successfully")
                .register(meterRegistry);
        failedCounter = Counter.builder("sse.messages.failed")
                .description("Total SSE messages failed to send")
                .register(meterRegistry);
        sendTimer = Timer.builder("sse.send.latency")
                .description("Latency of individual SSE send operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        meterRegistry.gauge("sse.connections.active", activeConnections, AtomicInteger::get);
    }

    /**
     * 注册 SSE 连接（在创建 SseEmitter 后调用）
     */
    public void trackConnection(SseEmitter emitter) {
        if (emitter != null && trackedEmitters.putIfAbsent(emitter, Boolean.TRUE) == null) {
            activeConnections.incrementAndGet();
            emitter.onCompletion(() -> {
                trackedEmitters.remove(emitter);
                activeConnections.decrementAndGet();
            });
            emitter.onTimeout(() -> {
                trackedEmitters.remove(emitter);
                activeConnections.decrementAndGet();
            });
            emitter.onError( _ -> {
                trackedEmitters.remove(emitter);
                activeConnections.decrementAndGet();
            });
        }
    }

    /**
     * 发送指定事件
     */
    public void send(SseEmitter emitter, String event, Object data) {
        if (emitter == null) {
            return;
        }
        long start = System.nanoTime();
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
            sentCounter.increment();
            sendTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (IOException | IllegalStateException e) {
            failedCounter.increment();
            log.debug("SSE 事件发送失败：event={}, err={}", event, e.getMessage());
        }
    }

    /**
     * 完成连接
     */
    public void complete(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE 连接关闭异常：{}", e.getMessage());
        }
    }

    /**
     * 以 error 事件结束
     */
    public void completeWithError(SseEmitter emitter, Object data) {
        send(emitter, "error", data);
        complete(emitter);
    }
}
