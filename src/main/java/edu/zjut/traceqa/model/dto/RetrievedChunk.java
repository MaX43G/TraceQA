package edu.zjut.traceqa.model.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索到的文本片段。
 */
@Data
@NoArgsConstructor
public class RetrievedChunk {

    /** LightRAG 引用 ID */
    private String referenceId;

    /** 来源文件路径 */
    private String filePath;

    /** 片段内容 */
    private String content;

    /** 融合得分 */
    private double score;

    /** 来源路径（graph 图谱 / vector 向量） */
    private String source;

    /** 章节路径（如 Section 1 → Subsection 1.2），可空 */
    private List<String> headings;

    public RetrievedChunk(String referenceId, String filePath, String content, double score, String source,
                          List<String> headings) {
        this.referenceId = referenceId;
        this.filePath = filePath;
        this.content = content;
        this.score = score;
        this.source = source;
        this.headings = headings;
    }
}