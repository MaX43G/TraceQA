package edu.zjut.traceqa.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义 LLM 配置（OpenAI 兼容）。
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
     * API Key
     */
    private String apiKey;

    /**
     * 模型名
     */
    private String model;

    /**
     * 三者均非空时有效
     */
    public boolean isValid() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}