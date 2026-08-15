package edu.zjut.traceqa.dto.model;

/**
 * 模型信息 DTO（模型选择功能）。
 *
 * @param name    展示名称
 * @param model   模型标识
 * @param baseUrl OpenAI 兼容地址（默认模型可为空）
 * @param isDefault 是否默认模型
 */
public record ModelVO(
        String name,
        String model,
        String baseUrl,
        boolean isDefault
) {
}