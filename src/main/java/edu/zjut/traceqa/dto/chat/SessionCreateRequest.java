package edu.zjut.traceqa.dto.chat;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求 DTO。
 *
 * @param title           会话标题（为空则取首条消息摘要）
 * @param knowledgeBaseId 绑定的知识库 ID（可空）
 */
public record SessionCreateRequest(
        @Size(max = 128, message = "会话标题长度不能超过 128")
        String title,
        Long knowledgeBaseId
) {
}