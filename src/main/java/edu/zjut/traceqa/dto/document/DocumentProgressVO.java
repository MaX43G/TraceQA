package edu.zjut.traceqa.dto.document;

/**
 * 文档解析进度 DTO（经 SSE 推送）。
 *
 * @param documentId 文档 ID
 * @param trackId    解析任务 ID
 * @param status     当前状态（PENDING/PROCESSING/DONE/FAILED）
 * @param progress   进度百分比（0-100）
 * @param chunkCount 已解析分块数
 * @param entityCount 已解析实体数
 * @param relationCount 已解析关系数
 * @param message    过程描述
 */
public record DocumentProgressVO(
        Long documentId,
        String trackId,
        String status,
        int progress,
        Integer chunkCount,
        Integer entityCount,
        Integer relationCount,
        String message
) {
}