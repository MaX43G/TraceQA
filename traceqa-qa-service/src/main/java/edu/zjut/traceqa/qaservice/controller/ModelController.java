package edu.zjut.traceqa.qaservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.model.vo.ModelVO;
import edu.zjut.traceqa.qaservice.config.QaProperties;
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
 */
@Tag(name = "模型", description = "可用模型列表查询")
@RestController
@RequestMapping("/api/models")
public class ModelController {

    /**
     * 默认模型名兜底（配置缺失时使用）
     */
    private static final String FALLBACK_DEFAULT_MODEL = "THUDM/GLM-4-9B-0414";

    @Resource
    private QaProperties properties;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String defaultModelName;

    @Value("${spring.ai.openai.base-url:}")
    private String defaultBaseUrl;

    /**
     * 查询可用模型列表。
     *
     * <p>保证至少返回一个非空的默认模型（避免前端下拉框出现空选项）。
     * 默认模型名优先取 {@code spring.ai.openai.chat.options.model}，为空时回退到
     * 配置列表中首个模型名，再兜底到 {@link #FALLBACK_DEFAULT_MODEL}。</p>
     */
    @Operation(summary = "查询可用模型列表")
    @GetMapping
    public ApiResponse<List<ModelVO>> list() {
        String effectiveDefault = resolveDefaultModel();
        List<ModelVO> result = new ArrayList<>();
        boolean hasDefault = false;
        for (QaProperties.ModelItem item : properties.getModels()) {
            String model = item.getModel() == null || item.getModel().isBlank()
                    ? effectiveDefault : item.getModel();
            String baseUrl = item.getBaseUrl() == null || item.getBaseUrl().isBlank()
                    ? defaultBaseUrl : item.getBaseUrl();
            String name = item.getName() == null || item.getName().isBlank() ? model : item.getName();
            boolean isDefault = effectiveDefault.equals(model);
            if (isDefault) {
                hasDefault = true;
            }
            result.add(new ModelVO(name, model, baseUrl, isDefault));
        }
        if (!hasDefault) {
            result.addFirst(new ModelVO(effectiveDefault, effectiveDefault, defaultBaseUrl, true));
        }
        return ApiResponse.ok(result);
    }

    /**
     * 解析默认模型名（保证非空）。
     */
    private String resolveDefaultModel() {
        if (defaultModelName != null && !defaultModelName.isBlank()) {
            return defaultModelName;
        }
        for (QaProperties.ModelItem item : properties.getModels()) {
            if (item.getModel() != null && !item.getModel().isBlank()) {
                return item.getModel();
            }
        }
        return FALLBACK_DEFAULT_MODEL;
    }
}