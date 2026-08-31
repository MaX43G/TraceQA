package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息视图。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageVO {

    /**
     * 消息 ID
     */
    private Long id;

    /**
     * 所属会话 ID
     */
    private Long sessionId;

    /**
     * 角色：USER / ASSISTANT
     */
    private String role;

    /**
     * 消息内容（Markdown）
     */
    private String content;

    /**
     * Agent 思考链路
     */
    private List<ThinkingNodeVO> thinkingTrace;

    /**
     * 引用来源
     */
    private List<ReferenceVO> references;

    /**
     * 生成耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 实体 + JSON 解析结果转 VO
     */
    public static ChatMessageVO of(ChatMessage message, List<ThinkingNodeVO> thinkingTrace, List<ReferenceVO> references) {
        return new ChatMessageVO(message.getId(), message.getSessionId(), message.getRole(),
                message.getContent(), thinkingTrace, references, message.getLatencyMs(), message.getCreateTime());
    }
}