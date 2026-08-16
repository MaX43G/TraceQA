package edu.zjut.traceqa.retrieval;

import java.util.List;

/**
 * 查询增强结果。
 *
 * @param original   原始用户问题
 * @param rewritten  查询重写后的独立查询
 * @param hyde       假设性文档（用于语义向量检索）
 * @param subqueries 查询分解出的子问题（对比类问题拆分为多个检索变体，可空）
 */
public record EnhancedQuery(
        String original,
        String rewritten,
        String hyde,
        List<String> subqueries
) {

    /** 便捷构造：无子问题 */
    public EnhancedQuery(String original, String rewritten, String hyde) {
        this(original, rewritten, hyde, List.of());
    }
}
