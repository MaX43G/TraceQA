package edu.zjut.traceqa.dto.chat;

import edu.zjut.traceqa.entity.ChatSession;

import java.time.LocalDateTime;

/**
 * 会话信息 DTO。
 */
public record SessionVO(
        Long id,
        String title,
        Long knowledgeBaseId,
        Integer pinned,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    /** 由会话实体组装 */
    public static SessionVO of(ChatSession session) {
        return new SessionVO(
                session.getId(),
                session.getTitle(),
                session.getKnowledgeBaseId(),
                session.getPinned(),
                session.getCreateTime(),
                session.getUpdateTime()
        );
    }
}