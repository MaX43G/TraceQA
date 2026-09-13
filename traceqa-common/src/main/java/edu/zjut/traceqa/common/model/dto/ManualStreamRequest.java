package edu.zjut.traceqa.common.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 手动检索模式流式对话请求。
 *
 * <p>与 {@link ChatStreamRequest} 分离，手动模式不经过 Agent 策略调度，
 * 直接根据用户选择的检索方式执行对应的检索路径。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ManualStreamRequest {

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
     * 图谱检索子模式：local / global / both（默认 both）
     */
    private String graphMode;

    /**
     * 选择的检索策略列表（至少选择一种）
     * 可选值：hyde, vector, keyword, graph
     */
    @NotEmpty(message = "请至少选择一种检索方式")
    private List<String> strategies;

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

    public boolean hasServerModel() {
        return serverModel != null && !serverModel.isBlank();
    }

    public boolean hasCustomModel() {
        return model != null && !model.isBlank();
    }

    public boolean useHyde() {
        return strategies != null && strategies.contains("hyde");
    }

    public boolean useVector() {
        return strategies != null && strategies.contains("vector");
    }

    public boolean useKeyword() {
        return strategies != null && strategies.contains("keyword");
    }

    public boolean useGraph() {
        return strategies != null && strategies.contains("graph");
    }

    /**
     * 图谱子模式，默认 both（local + global）
     */
    public String getGraphModeOrDefault() {
        if (graphMode == null || graphMode.isBlank()) {
            return "both";
        }
        String mode = graphMode.trim().toLowerCase();
        return switch (mode) {
            case "local", "global" -> mode;
            default -> "both";
        };
    }
}
