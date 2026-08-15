package edu.zjut.traceqa.dto.chat;

/**
 * 引用来源 DTO。
 *
 * <p>前端渲染为可点击的「角标」，点击后高亮展示原始文本。</p>
 *
 * @param index    引用序号（对应回答中的 [citation:N]）
 * @param title    来源标题（通常是文件名）
 * @param filePath LightRAG 来源文件路径
 * @param content  原始片段文本
 */
public record ReferenceVO(
        Integer index,
        String title,
        String filePath,
        String content
) {
}