package edu.zjut.traceqa.common.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量导入文档结果。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchUploadVO {

    /**
     * 成功数量
     */
    private int successCount;

    /**
     * 失败数量
     */
    private int failedCount;

    /**
     * 失败明细
     */
    private List<String> errors;
}