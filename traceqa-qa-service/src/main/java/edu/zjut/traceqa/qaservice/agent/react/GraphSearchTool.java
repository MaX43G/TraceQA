package edu.zjut.traceqa.qaservice.agent.react;

import edu.zjut.traceqa.common.model.dto.RetrievedChunk;
import edu.zjut.traceqa.qaservice.retrieval.RetrievalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图谱检索工具。
 *
 * <p>基于 LightRAG 知识图谱的实体关系检索，适合需要关系推理的问题。</p>
 */
@Component
public class GraphSearchTool implements ReActTool {

    @Resource
    private RetrievalService retrievalService;

    @Override
    public String name() {
        return "graph_search";
    }

    @Override
    public String description() {
        return "知识图谱检索：基于知识图谱中的实体和关系进行检索。适合需要实体关系推理、因果推导、跨文档关联的问题。";
    }

    @Override
    public String execute(String query) {
        List<RetrievedChunk> chunks = retrievalService.queryGraph(query, null);
        return formatResults(chunks);
    }

    private String formatResults(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "[图谱检索] 未找到相关结果。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[图谱检索] 找到 ").append(chunks.size()).append(" 条相关结果：\n\n");
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
