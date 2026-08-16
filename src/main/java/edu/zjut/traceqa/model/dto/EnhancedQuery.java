package edu.zjut.traceqa.model.dto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询增强结果。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnhancedQuery {

    /** 原始用户问题 */
    private String original;

    /** 查询重写后的独立查询 */
    private String rewritten;

    /** 假设性文档（用于语义向量检索） */
    private String hyde;

    /** 查询分解出的子问题（对比类问题拆分为多个检索变体，可空） */
    private List<String> subqueries;

/** 便捷构造：无子问题 */
    public EnhancedQuery(String original, String rewritten, String hyde) {
        this(original, rewritten, hyde, List.of());
    }
}
