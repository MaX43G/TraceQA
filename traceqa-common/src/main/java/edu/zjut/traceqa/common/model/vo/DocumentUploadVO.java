package edu.zjut.traceqa.common.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单文档上传结果（HTTP 202）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentUploadVO {

    /**
     * 文档 ID
     */
    private Long documentId;
}