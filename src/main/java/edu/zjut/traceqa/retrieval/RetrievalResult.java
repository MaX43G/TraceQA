package edu.zjut.traceqa.retrieval;

import java.util.List;

/**
 * 双路检索融合结果。
 *
 * @param chunks   融合排序后的检索片段
 * @param degraded 是否发生了降级（如未走查询增强）
 */
public record RetrievalResult(
        List<RetrievedChunk> chunks,
        boolean degraded
) {

    /** 判断是否检索到可用内容 */
    public boolean hasContent() {
        return chunks != null && !chunks.isEmpty();
    }
}