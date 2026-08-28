package edu.zjut.traceqa.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 双路检索融合结果。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalResult {

    /**
     * 融合排序后的检索片段
     */
    private List<RetrievedChunk> chunks;

    /**
     * 是否发生了降级（如未走查询增强）
     */
    private boolean degraded;

    /**
     * 判断是否检索到可用内容
     */
    public boolean hasContent() {
        return chunks != null && !chunks.isEmpty();
    }
}
