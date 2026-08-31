package edu.zjut.traceqa.qaservice.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * SSE 事件发布器。
 *
 * <p>统一封装结构化的 {@code event + data} 写入，所有失败均记录并安全忽略，
 * 保证流式推送不因单次失败中断整体编排。</p>
 */
@Component
public class SsePublisher {

    private static final Logger log = LoggerFactory.getLogger(SsePublisher.class);

    private final ObjectMapper objectMapper;

    public SsePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 发送指定事件
     */
    public synchronized void send(SseEmitter emitter, String event, Object data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
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