package edu.zjut.traceqa.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 聊天流式请求 DTO。
 *
 * @param sessionId       会话 ID（为空则自动新建会话）
 * @param knowledgeBaseId 知识库 ID（为空则全局检索）
 * @param content         用户消息内容
 * @param model           自定义模型名称（仅使用自定义模型时提供）
 * @param baseUrl         自定义模型 OpenAI 兼容地址（仅使用自定义模型时提供）
 * @param apiKey          自定义模型 API Key（仅本次请求使用，不持久化）
 */
public record ChatStreamRequest(
        Long sessionId,
        Long knowledgeBaseId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容过长")
        String content,

        @Size(max = 128, message = "模型名称过长")
        String model,

        @Size(max = 512, message = "接口地址过长")
        String baseUrl,

        @Size(max = 256, message = "API Key 过长")
        String apiKey
) {

    /** 是否携带了自定义模型配置 */
    public boolean hasCustomModel() {
        return model != null && !model.isBlank();
    }
}