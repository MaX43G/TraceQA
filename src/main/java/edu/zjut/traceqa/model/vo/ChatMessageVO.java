package edu.zjut.traceqa.model.vo;

import edu.zjut.traceqa.model.po.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息 DTO（含解析后的思考链路与引用）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageVO {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private List<ThinkingNodeVO> thinkingTrace;

    private List<ReferenceVO> references;

    private Long latencyMs;

    private LocalDateTime createTime;

    /**
     * 由消息实体 + 已解析列表组装
     */
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
