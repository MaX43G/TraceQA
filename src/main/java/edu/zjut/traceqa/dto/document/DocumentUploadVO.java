package edu.zjut.traceqa.dto.document;

/**
 * 文档上传响应 DTO（HTTP 202 Accepted）。
 *
 * @param documentId 文档记录 ID
 * @param trackId    异步解析任务 ID（用于进度追踪）
 */
public record DocumentUploadVO(
        Long documentId,
        String trackId
) {
}