package edu.zjut.traceqa.dto.document;

import java.util.List;

/**
 * 批量导入结果 DTO。
 *
 * @param successCount 成功导入的文档数
 * @param failedCount  失败数
 * @param errors       失败明细（文件名 + 原因）
 */
public record BatchUploadVO(
        int successCount,
        int failedCount,
        List<String> errors
) {
}
