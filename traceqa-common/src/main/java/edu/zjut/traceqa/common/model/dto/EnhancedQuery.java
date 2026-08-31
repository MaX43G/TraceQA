package edu.zjut.traceqa.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 增强查询结果（查询重写 + HyDE + 子问题分解）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnhancedQuery {

    /**
     * 原始问题
     */
    private String original;

    /**
     * 重写后的查询
     */
    private String rewritten;

    /**
     * HyDE 假设文档
     */
    private String hyde;

    /**
     * 分解出的子问题列表
     */
    private List<String> subqueries;

    /**
     * 便捷构造（无子问题）
     */
    public EnhancedQuery(String original, String rewritten, String hyde) {
        this(original, rewritten, hyde, List.of());
    }
}