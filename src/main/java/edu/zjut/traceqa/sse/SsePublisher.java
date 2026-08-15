package edu.zjut.traceqa.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 事件发布器。
 *
 * <p>统一负责向 {@link SseEmitter} 写入结构化事件（{@code event + data}），
 * 所有写入失败均记录日志并安全忽略，保证主业务流程不受影响。</p>
 */
@Slf4j
@Component
public class SsePublisher {

    private final ObjectMapper objectMapper;

    public SsePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 写入一个 SSE 事件 */
    public void send(SseEmitter emitter, String event, Object data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            // 连接已断开或序列化失败：仅记录日志，忽略异常
            log.debug("SSE 事件发送失败：event={}, err={}", event, e.getMessage());
        }
    }

    /** 完成推送并关闭连接 */
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

    /** 推送错误并关闭连接 */
    public void completeWithError(SseEmitter emitter, Object data) {
        send(emitter, "error", data);
        complete(emitter);
    }
}