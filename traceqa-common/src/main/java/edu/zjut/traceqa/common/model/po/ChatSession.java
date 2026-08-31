package edu.zjut.traceqa.common.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天会话实体。
 *
 * <p>一个会话内包含多条 {@link ChatMessage}。支持逻辑删除（{@code deleted} 标记），
 * 删除后前端不再展示，底层数据保留可审计。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_session")
public class ChatSession extends BaseEntity {

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 会话标题（默认取首条用户消息）
     */
    private String title;

    /**
     * 绑定的知识库 ID（可空，空表示全局检索）
     */
    private Long knowledgeBaseId;

    /**
     * 置顶标记：1 置顶，0 普通
     */
    private Integer pinned;

    /**
     * 会话状态：1 正常，0 归档
     */
    private Integer status;
}