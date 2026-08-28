package edu.zjut.traceqa.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文档实体。
 *
 * <p>记录上传文档的元信息与异步解析进度。文件本体存于本地文件系统
 * {@code app.storage.root}，数据库仅保存路径引用，杜绝额外引入对象存储中间件。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@TableName("t_document")
public class Document extends BaseEntity {

    /**
     * 所属知识库 ID
     */
    private Long knowledgeBaseId;

    /**
     * 原始文件名（含扩展名）
     */
    private String originalName;

    /**
     * 本地存储的相对路径
     */
    private String storedPath;

    /**
     * 文件类型（扩展名，如 pdf/pptx/docx）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 解析状态（见 {@link DocumentStatus}）
     */
    private String status;

    /**
     * LightRAG 返回的任务追踪 ID（track_id）
     */
    private String trackId;

    /**
     * 内容指纹（SHA-256，用于去重）
     */
    private String contentHash;

    /**
     * 切分后的子文件总数（>=1，未切分为 1）
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
     * 失败原因（降级提示，不暴露堆栈）
     */
    private String errorMsg;
}