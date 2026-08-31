package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档视图。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentVO {

    /**
     * 文档 ID
     */
    private Long id;

    /**
     * 所属知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件类型（扩展名）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 解析状态（PENDING/PROCESSING/DONE/FAILED）
     */
    private String status;

    /**
     * 切分后的子文件总数
     */
    private Integer partTotal;

    /**
     * 已完成解析的子文件数
     */
    private Integer partDone;

    /**
     * 抽取出的分块数量
     */
    private Integer chunkCount;

    /**
     * 抽取出的实体数量
     */
    private Integer entityCount;

    /**
     * 抽取出的关系数量
     */
    private Integer relationCount;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 实体转 VO
     */
    public static DocumentVO of(Document doc) {
        return new DocumentVO(doc.getId(), doc.getKnowledgeBaseId(), doc.getOriginalName(),
                doc.getFileType(), doc.getFileSize(), doc.getStatus(), doc.getPartTotal(),
                doc.getPartDone(), doc.getChunkCount(), doc.getEntityCount(), doc.getRelationCount(),
                doc.getErrorMsg(), doc.getCreateTime(), doc.getUpdateTime());
    }
}