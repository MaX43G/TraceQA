package edu.zjut.traceqa.dto.document;

import edu.zjut.traceqa.entity.Document;

import java.time.LocalDateTime;

/**
 * 文档信息 DTO。
 */
public record DocumentVO(
        Long id,
        Long knowledgeBaseId,
        String originalName,
        String fileType,
        Long fileSize,
        String status,
        Integer chunkCount,
        Integer entityCount,
        Integer relationCount,
        String errorMsg,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    /** 由文档实体组装 */
    public static DocumentVO of(Document doc) {
        return new DocumentVO(
                doc.getId(),
                doc.getKnowledgeBaseId(),
                doc.getOriginalName(),
                doc.getFileType(),
                doc.getFileSize(),
                doc.getStatus(),
                doc.getChunkCount(),
                doc.getEntityCount(),
                doc.getRelationCount(),
                doc.getErrorMsg(),
                doc.getCreateTime(),
                doc.getUpdateTime()
        );
    }
}