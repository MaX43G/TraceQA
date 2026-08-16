package edu.zjut.traceqa.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.config.AppProperties;
import edu.zjut.traceqa.model.vo.ModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型接口（模型选择功能）。
 *
 * <p>返回服务端已配置的可用模型列表（默认一个，后续可扩展），
 * 前端据此展示「模型选择」下拉框，并支持用户自定义 OpenAI 兼容模型（本地存储）。</p>
 */
@Tag(name = "模型", description = "可用模型列表查询")
@RestController
@RequestMapping("/api/models")
public class ModelController {

    @Resource
    private AppProperties properties;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String defaultModelName;

    @Value("${spring.ai.openai.base-url:}")
    private String defaultBaseUrl;

    @Operation(summary = "查询可用模型列表")
    @GetMapping
    public ApiResponse<List<ModelVO>> list() {
        List<ModelVO> result = new ArrayList<>();
        boolean hasDefault = false;
        for (AppProperties.ModelItem item : properties.getModels()) {
            String model = item.getModel() == null || item.getModel().isBlank()
                    ? defaultModelName : item.getModel();
            String baseUrl = item.getBaseUrl() == null || item.getBaseUrl().isBlank()
                    ? defaultBaseUrl : item.getBaseUrl();
            // 默认模型：模型名与 Spring AI 默认模型一致
            boolean isDefault = defaultModelName != null && defaultModelName.equals(model);
            if (isDefault) {
                hasDefault = true;
            }
            result.add(new ModelVO(item.getName(), model, baseUrl, isDefault));
        }
        // 兜底：确保至少返回一个默认模型
        if (!hasDefault) {
            result.add(0, new ModelVO(defaultModelName, defaultModelName, defaultBaseUrl, true));
        }
        return ApiResponse.ok(result);
    }
}