package edu.zjut.traceqa.retrieval;

/**
 * 检索到的文本片段。
 *
 * @param referenceId LightRAG 引用 ID
 * @param filePath    来源文件路径
 * @param content     片段内容
 * @param score       融合得分
 * @param source      来源路径（graph 图谱 / vector 向量）
 */
public record RetrievedChunk(
        String referenceId,
        String filePath,
        String content,
        double score,
        String source
) {
}