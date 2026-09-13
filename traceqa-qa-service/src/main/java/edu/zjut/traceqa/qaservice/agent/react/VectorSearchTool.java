package edu.zjut.traceqa.qaservice.agent.react;

import edu.zjut.traceqa.common.model.dto.EnhancedQuery;
import edu.zjut.traceqa.common.model.dto.RetrievedChunk;
import edu.zjut.traceqa.qaservice.retrieval.RetrievalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量检索工具。
 *
 * <p>基于 LightRAG 的向量语义检索，适合语义相似度匹配。</p>
 */
@Component
public class VectorSearchTool implements ReActTool {

    @Resource
    private RetrievalService retrievalService;

    @Override
    public String name() {
        return "vector_search";
    }

    @Override
    public String description() {
        return "向量语义检索：基于文本语义相似度在知识库中检索相关片段。适合需要语义理解的问题，如概念解释、原理阐述等。";
    }

    @Override
    public String execute(String query) {
        EnhancedQuery enhanced = new EnhancedQuery(query, null, null);
        List<RetrievedChunk> chunks = retrievalService.queryVector(query, enhanced, null);
        return formatResults(chunks);
    }

    private String formatResults(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "[向量检索] 未找到相关结果。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[向量检索] 找到 ").append(chunks.size()).append(" 条相关结果：\n\n");
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
