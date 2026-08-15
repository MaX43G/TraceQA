package edu.zjut.traceqa.dto.chat;

import edu.zjut.traceqa.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息 DTO（含解析后的思考链路与引用）。
 */
public record ChatMessageVO(
        Long id,
        Long sessionId,
        String role,
        String content,
        List<ThinkingNodeVO> thinkingTrace,
        List<ReferenceVO> references,
        Long latencyMs,
        LocalDateTime createTime
) {

    /** 由消息实体 + 已解析列表组装 */
    public static ChatMessageVO of(ChatMessage message,
                                   List<ThinkingNodeVO> thinkingTrace,
                                   List<ReferenceVO> references) {
        return new ChatMessageVO(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                thinkingTrace,
                references,
                message.getLatencyMs(),
                message.getCreateTime()
        );
    }
}