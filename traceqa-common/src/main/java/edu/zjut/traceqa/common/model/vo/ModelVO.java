package edu.zjut.traceqa.common.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可用模型视图。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelVO {

    /**
     * 展示名
     */
    private String name;

    /**
     * 模型名
     */
    private String model;

    /**
     * Base URL
     */
    private String baseUrl;

    /**
     * 是否为默认模型
     */
    private boolean isDefault;
}