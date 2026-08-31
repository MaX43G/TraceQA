package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.ChatSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话视图。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVO {

    /**
     * 会话 ID
     */
    private Long id;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 绑定的知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 置顶标记
     */
    private Integer pinned;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 实体转 VO
     */
    public static SessionVO of(ChatSession session) {
        return new SessionVO(session.getId(), session.getTitle(), session.getKnowledgeBaseId(),
                session.getPinned(), session.getCreateTime(), session.getUpdateTime());
    }
}