package edu.zjut.traceqa.dto.document;

/**
 * 文档上传响应 DTO（HTTP 202 Accepted）。
 *
 * @param documentId 文档记录 ID（用于查询列表与进度追踪）
 */
public record DocumentUploadVO(
        Long documentId
) {
}