package edu.zjut.traceqa.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档上传响应 DTO（HTTP 202 Accepted）。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentUploadVO {

    /**
     * 文档记录 ID（用于查询列表与进度追踪）
     */
    private Long documentId;

}
