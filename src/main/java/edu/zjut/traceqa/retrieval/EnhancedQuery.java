package edu.zjut.traceqa.retrieval;

/**
 * 查询增强结果。
 *
 * @param original  原始用户问题
 * @param rewritten 查询重写后的独立查询
 * @param hyde      假设性文档（用于语义向量检索）
 */
public record EnhancedQuery(
        String original,
        String rewritten,
        String hyde
) {
}