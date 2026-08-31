package edu.zjut.traceqa.common.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索到的单个片段。
 */
@Data
@NoArgsConstructor
public class RetrievedChunk {

    /**
     * LightRAG 引用 ID
     */
    private String referenceId;

    /**
     * 来源文件路径
     */
    private String filePath;

    /**
     * 片段文本
     */
    private String content;

    /**
     * 融合得分
     */
    private double score;

    /**
     * 来源类型（graph/vector/keyword/reread/retry）
     */
    private String source;

    /**
     * 章节路径
     */
    private List<String> headings;

    /**
     * 全参构造
     */
    public RetrievedChunk(String referenceId, String filePath, String content, double score, String source, List<String> headings) {
        this.referenceId = referenceId;
        this.filePath = filePath;
        this.content = content;
        this.score = score;
        this.source = source;
        this.headings = headings;
    }
}