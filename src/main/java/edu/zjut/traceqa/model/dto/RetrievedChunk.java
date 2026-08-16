package edu.zjut.traceqa.model.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索到的文本片段。
 *
 */
@Data
@AllArgsConstructor
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

}
