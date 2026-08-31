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

    @Resource
    private QaProperties properties;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String defaultModelName;

    @Value("${spring.ai.openai.base-url:}")
    private String defaultBaseUrl;

    /**
     * 查询可用模型列表
     */
    @Operation(summary = "查询可用模型列表")
    @GetMapping
    public ApiResponse<List<ModelVO>> list() {
        List<ModelVO> result = new ArrayList<>();
        boolean hasDefault = false;
        for (QaProperties.ModelItem item : properties.getModels()) {
            String model = item.getModel() == null || item.getModel().isBlank()
                    ? defaultModelName : item.getModel();
            String baseUrl = item.getBaseUrl() == null || item.getBaseUrl().isBlank()
                    ? defaultBaseUrl : item.getBaseUrl();
            boolean isDefault = defaultModelName != null && defaultModelName.equals(model);
            if (isDefault) {
                hasDefault = true;
            }
            result.add(new ModelVO(item.getName(), model, baseUrl, isDefault));
        }
        if (!hasDefault) {
            result.addFirst(new ModelVO(defaultModelName, defaultModelName, defaultBaseUrl, true));
        }
        return ApiResponse.ok(result);
    }
}