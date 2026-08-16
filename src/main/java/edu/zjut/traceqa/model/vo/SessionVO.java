package edu.zjut.traceqa.model.vo;
import edu.zjut.traceqa.model.po.ChatSession;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVO {

    private Long id;

    private String title;

    private Long knowledgeBaseId;

    private Integer pinned;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

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
