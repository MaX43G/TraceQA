package edu.zjut.traceqa.common.model.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天消息实体。
 *
 * <p>包含消息正文、思考链路（Agent 各节点状态 JSON）、引用来源列表（JSON），
 * 以及消息级逻辑删除标记。正文支持 Markdown 渲染。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_message")
public class ChatMessage extends BaseEntity {

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
     * Agent 思考链路 JSON 数组（节点名/状态/耗时）
     */
    @TableField("thinking_trace")
    private String thinkingTrace;

    /**
     * 引用来源 JSON 数组（references 为 MySQL 保留字需反引号包裹）
     */
    @TableField("`references`")
    private String references;

    /**
     * 生成耗时（毫秒，AI 消息）
     */
    private Long latencyMs;

    /**
     * 消息状态：1 正常
     */
    private Integer status;
}