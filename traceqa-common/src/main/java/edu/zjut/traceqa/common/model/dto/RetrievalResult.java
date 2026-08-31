package edu.zjut.traceqa.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索结果聚合。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalResult {

    /**
     * 融合后的检索片段
     */
    private List<RetrievedChunk> chunks;

    /**
     * 是否降级（重写/HyDE 缺失）
     */
    private boolean degraded;

    /**
     * 是否含有效内容
     */
    public boolean hasContent() {
        return chunks != null && !chunks.isEmpty();
    }
}