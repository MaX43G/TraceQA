package edu.zjut.traceqa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 模型配置（OpenAI 兼容格式）。
 *
 * <p>用于支持「自定义模型」：由前端将用户填写的 baseUrl / apiKey / model
 * 随请求传入，后端仅用于本次调用（不持久化），满足「本地存储、不上传云端」的要求。</p>
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LlmConfig {

    /**
     * OpenAI 兼容接口地址
     */
    private String baseUrl;

    /**
     * API Key（仅本次请求使用）
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 判断是否为有效配置
     */
    public boolean isValid() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
