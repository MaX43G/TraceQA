package edu.zjut.traceqa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天流式请求 DTO。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatStreamRequest {

    /**
     * 会话 ID（为空则自动新建会话）
     */
    private Long sessionId;

    /**
     * 知识库 ID（为空则全局检索）
     */
    private Long knowledgeBaseId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容过长")

    /**
     * 用户消息内容
     */
    private String content;

    @Size(max = 128, message = "模型名称过长")
    /**
     * 选中的服务端模型名（如 Qwen/Qwen3-8B，走平台默认 Base URL/API Key）
     */
    private String serverModel;

    @Size(max = 128, message = "模型名称过长")
    /**
     * 自定义模型名称（仅使用自定义模型时提供）
     */
    private String model;

    @Size(max = 512, message = "接口地址过长")
    /**
     * 自定义模型 OpenAI 兼容地址（仅使用自定义模型时提供）
     */
    private String baseUrl;

    @Size(max = 256, message = "API Key 过长")
    /**
     * 自定义模型 API Key（仅本次请求使用，不持久化）
     */
    private String apiKey;

    /**
     * 是否选择了服务端模型（非默认模型切换）
     */
    public boolean hasServerModel() {
        return serverModel != null && !serverModel.isBlank();
    }

    /**
     * 是否携带了自定义模型配置
     */
    public boolean hasCustomModel() {
        return model != null && !model.isBlank();
    }
}
