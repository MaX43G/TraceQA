package edu.zjut.traceqa.model.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档解析进度 DTO（经 SSE 推送）。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentProgressVO {

    /** 文档 ID */
    private Long documentId;

    /** 解析任务 ID */
    private String trackId;

    /** 当前状态（PENDING/PROCESSING/DONE/FAILED） */
    private String status;

    /** 进度百分比（0-100） */
    private int progress;

    /** 切分后的子文件总数 */
    private Integer partTotal;

    /** 已完成解析的子文件数 */
    private Integer partDone;

    /** 已解析分块数 */
    private Integer chunkCount;

    /** 已解析实体数 */
    private Integer entityCount;

    /** 已解析关系数 */
    private Integer relationCount;

    /** 过程描述 */
    private String message;

}
