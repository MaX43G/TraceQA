package edu.zjut.traceqa.model.vo;

import edu.zjut.traceqa.model.po.Document;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档信息 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentVO {

    private Long id;

    private Long knowledgeBaseId;

    private String originalName;

    private String fileType;

    private Long fileSize;

    private String status;

    private Integer partTotal;

    private Integer partDone;

    private Integer chunkCount;

    private Integer entityCount;

    private Integer relationCount;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 由文档实体组装
     */
    public static DocumentVO of(Document doc) {
        return new DocumentVO(
                doc.getId(),
                doc.getKnowledgeBaseId(),
                doc.getOriginalName(),
                doc.getFileType(),
                doc.getFileSize(),
                doc.getStatus(),
                doc.getPartTotal(),
                doc.getPartDone(),
                doc.getChunkCount(),
                doc.getEntityCount(),
                doc.getRelationCount(),
                doc.getErrorMsg(),
                doc.getCreateTime(),
                doc.getUpdateTime()
        );
    }
}
