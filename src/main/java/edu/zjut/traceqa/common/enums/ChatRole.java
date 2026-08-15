package edu.zjut.traceqa.common.enums;

import lombok.Getter;

/**
 * 聊天消息角色枚举。
 *
 * <p>用于区分对话中用户与 AI 助手产生的消息，支撑对话历史管理与 Markdown 导出。</p>
 */
@Getter
public enum ChatRole {

    /** 用户消息 */
    USER("用户"),
    /** AI 助手消息 */
    ASSISTANT("AI");

    /** 中文展示名 */
    private final String label;

    ChatRole(String label) {
        this.label = label;
    }
}