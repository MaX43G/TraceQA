package edu.zjut.traceqa.common.model.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Elasticsearch 文档片段（用于 BM25 关键词全文检索）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsChunk {

    /** 文档 ID（关联 t_document.id） */
    private Long documentId;

    /** 所属知识库 ID */
    private Long knowledgeBaseId;

    /** 原始文件名 */
    private String fileName;

    /** 片段文本内容 */
    private String content;

    /** 章节路径（Markdown 标题层级） */
    private List<String> headings;

    /** 片段序号（同一文档内的 chunk 序号） */
    private int chunkIndex;
}
