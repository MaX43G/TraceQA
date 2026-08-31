package edu.zjut.traceqa.common.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 引用来源视图。
 */
@Data
@NoArgsConstructor
public class ReferenceVO {

    /**
     * 引用序号，对应 [citation:N]
     */
    private Integer index;

    /**
     * 来源标题（通常是文件名）
     */
    private String title;

    /**
     * 来源文件路径
     */
    private String filePath;

    /**
     * 原始片段文本
     */
    private String content;

    /**
     * 章节路径，可空
     */
    private List<String> headings;

    /**
     * 命中高亮术语，可空
     */
    private List<String> highlight;

    /**
     * 便捷构造（无章节/高亮）
     */
    public ReferenceVO(Integer index, String title, String filePath, String content) {
        this(index, title, filePath, content, null, null);
    }

    /**
     * 全参构造
     */
    public ReferenceVO(Integer index, String title, String filePath, String content,
                       List<String> headings, List<String> highlight) {
        this.index = index;
        this.title = title;
        this.filePath = filePath;
        this.content = content;
        this.headings = headings;
        this.highlight = highlight;
    }
}