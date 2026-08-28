package edu.zjut.traceqa.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型信息 DTO（模型选择功能）。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelVO {

    /**
     * 展示名称
     */
    private String name;

    /**
     * 模型标识
     */
    private String model;

    /**
     * OpenAI 兼容地址（默认模型可为空）
     */
    private String baseUrl;

    /**
     * 是否默认模型
     */
    private boolean isDefault;

}
