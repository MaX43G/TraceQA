package edu.zjut.traceqa.model.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用来源 DTO。
 *
 * <p>前端渲染为可点击的「角标」，点击后高亮展示原始文本。</p>
 *
 */
@Data
@AllArgsConstructor
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

}
