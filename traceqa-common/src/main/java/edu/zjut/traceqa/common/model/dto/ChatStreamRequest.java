package edu.zjut.traceqa.common.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式对话请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatStreamRequest {

    /**
     * 会话 ID（空则自动创建新会话）
     */
    private Long sessionId;

    /**
     * 绑定的知识库 ID（空表示全局检索）
     */
    private Long knowledgeBaseId;

    /**
     * 用户消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容过长")
    private String content;

    /**
     * 服务端已配置模型名（从模型列表选择）
     */
    @Size(max = 128)
    private String serverModel;

    /**
     * 自定义模型名（用户自填 OpenAI 兼容模型）
     */
    @Size(max = 128)
    private String model;

    /**
     * 自定义模型 Base URL
     */
    @Size(max = 512)
    private String baseUrl;

    /**
     * 自定义模型 API Key（不持久化）
     */
    @Size(max = 256)
    private String apiKey;

    /**
     * 是否启用向量检索（默认 true）
     */
    private Boolean enableVector;

    /**
     * 是否启用图谱检索（默认 true）
     */
    private Boolean enableGraph;

    /**
     * 是否启用关键词检索（默认 true）
     */
    private Boolean enableKeyword;

    /**
     * 是否使用服务端配置模型
     */
    public boolean hasServerModel() {
        return serverModel != null && !serverModel.isBlank();
    }

    /**
     * 是否使用自定义模型
     */
    public boolean hasCustomModel() {
        return model != null && !model.isBlank();
    }

    /**
     * 向量检索是否开启（null 视为 true）
     */
    public boolean isVectorEnabled() {
        return enableVector == null || enableVector;
    }

    /**
     * 图谱检索是否开启（null 视为 true）
     */
    public boolean isGraphEnabled() {
        return enableGraph == null || enableGraph;
    }

    /**
     * 关键词检索是否开启（null 视为 true）
     */
    public boolean isKeywordEnabled() {
        return enableKeyword == null || enableKeyword;
    }
}