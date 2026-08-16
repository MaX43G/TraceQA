package edu.zjut.traceqa.model.vo;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量导入结果 DTO。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchUploadVO {

    /** 成功导入的文档数 */
    private int successCount;

    /** 失败数 */
    private int failedCount;

    /** 失败明细（文件名 + 原因） */
    private List<String> errors;

}
