package edu.zjut.traceqa.qaservice.agent.react;

import edu.zjut.traceqa.common.model.dto.LlmConfig;
import edu.zjut.traceqa.common.model.dto.RetrievedChunk;
import edu.zjut.traceqa.qaservice.retrieval.RetrievalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关键词检索工具。
 *
 * <p>基于 jieba TF-IDF 关键词提取 + ES BM25 全文检索，适合精确术语匹配。</p>
 */
@Component
public class KeywordSearchTool implements ReActTool {

    @Resource
    private RetrievalService retrievalService;

    @Override
    public String name() {
        return "keyword_search";
    }

    @Override
    public String description() {
        return "关键词全文检索：基于 TF-IDF 关键词提取和 Elasticsearch BM25 全文检索。适合精确术语匹配、定义查询、参数查找等。";
    }

    @Override
    public String execute(String query) {
        List<RetrievedChunk> chunks = retrievalService.queryKeyword(query, null, null);
        return formatResults(chunks);
    }

    private String formatResults(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "[关键词检索] 未找到相关结果。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[关键词检索] 找到 ").append(chunks.size()).append(" 条相关结果：\n\n");
        int idx = 1;
        for (RetrievedChunk chunk : chunks) {
            sb.append("--- 结果 ").append(idx).append(" ---\n");
            if (chunk.getFilePath() != null && !chunk.getFilePath().isBlank()) {
                sb.append("来源：").append(extractFilename(chunk.getFilePath())).append("\n");
            }
            if (chunk.getHeadings() != null && !chunk.getHeadings().isEmpty()) {
                sb.append("章节：").append(String.join(" > ", chunk.getHeadings())).append("\n");
            }
            String content = chunk.getContent();
            if (content != null && content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            sb.append("内容：").append(content).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    private String extractFilename(String path) {
        if (path == null || !path.contains("/")) {
            return path == null ? "未知" : path;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
