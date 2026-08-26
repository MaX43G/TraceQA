package edu.zjut.traceqa.model.vo;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 引用来源 DTO。
 *
 * <p>前端渲染为可点击的「角标」，点击后高亮展示原始文本。</p>
 */
@Data
@NoArgsConstructor
public class ReferenceVO {

    /** 引用序号（对应回答中的 [citation:N]） */
    private Integer index;

    /** 来源标题（通常是文件名） */
    private String title;

    /** LightRAG 来源文件路径 */
    private String filePath;

    /** 原始片段文本 */
    private String content;

    /** 章节路径（如 Section 1 → Subsection 1.2），可空 */
    private List<String> headings;

    /** 命中高亮术语（来自用户问题，前端用于片段内高亮），可空 */
    private List<String> highlight;

    public ReferenceVO(Integer index, String title, String filePath, String content) {
        this(index, title, filePath, content, null, null);
    }

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