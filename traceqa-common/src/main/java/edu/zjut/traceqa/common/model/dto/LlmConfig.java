package edu.zjut.traceqa.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义 LLM 配置（OpenAI 兼容）。
 *
 * <p>当用户选择自定义模型或服务端模型切换时，前端传入此配置。
 * 三者均非空且格式合法时 {@link #isValid()} 返回 true。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LlmConfig {

    /**
     * OpenAI 兼容 Base URL
     */
    private String baseUrl;

    /**
     * API Key（敏感信息，不会序列化到日志）
     */
    private String apiKey;

    /**
     * 模型名
     */
    private String model;

    /**
     * 三者均非空且格式合法时有效
     */
    public boolean isValid() {
        return isValidUrl(baseUrl) && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

    /**
     * 校验 Base URL 格式（必须以 http:// 或 https:// 开头）
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    @Override
    public String toString() {
        // 防止 API Key 泄露到日志
        return "LlmConfig{baseUrl='" + baseUrl + "', apiKey='***', model='" + model + "'}";
    }
}