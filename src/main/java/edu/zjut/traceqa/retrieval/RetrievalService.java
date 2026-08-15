package edu.zjut.traceqa.retrieval;

import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.config.LlmConfig;
import edu.zjut.traceqa.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 双路检索与融合服务。
 *
 * <p>实现完整的 RAG 增强检索链路：</p>
 * <ol>
 *   <li><b>查询重写（Query Rewriting）</b>：将用户问题改写为可独立检索的查询；</li>
 *   <li><b>假设性文档嵌入（HyDE）</b>：生成假设答案段落用于语义向量检索；</li>
 *   <li><b>双路检索</b>：图谱路径（local）与向量路径（naive）并行查询；</li>
 *   <li><b>RRF 融合</b>：按倒数排名融合两路结果并去重；</li>
 *   <li><b>ReRead 二次检索</b>：抽取关键术语补充检索，查漏补缺。</li>
 * </ol>
 *
 * <p>任一路径失败均自动降级，绝不中断整个链路。</p>
 */
@Slf4j
@Service
public class RetrievalService {

    /** RRF 融合常数 */
    private static final double RRF_K = 60.0;
    /** 每路保留的最大结果数 */
    private static final int MAX_PER_PATH = 12;

    private final LightRagClient lightRagClient;
    private final LlmService llmService;

    public RetrievalService(LightRagClient lightRagClient, LlmService llmService) {
        this.lightRagClient = lightRagClient;
        this.llmService = llmService;
    }

    /**
     * 执行增强检索。
     *
     * @param question 用户原始问题
     * @return 融合后的检索结果（含增强查询信息）
     */
    public RetrievalResult retrieve(String question) {
        return retrieve(question, null);
    }

    /**
     * 执行增强检索。
     *
     * @param question 用户原始问题
     * @param config   自定义模型配置（null 表示使用默认模型）
     * @return 融合后的检索结果（含增强查询信息）
     */
    public RetrievalResult retrieve(String question, LlmConfig config) {
        return retrieve(question, config, null);
    }

    /**
     * 执行增强检索（支持进度回调，用于 SSE 实时提示）。
     */
    public RetrievalResult retrieve(String question, LlmConfig config, Consumer<String> progress) {
        EnhancedQuery enhanced = enhance(question, config, progress);
        List<RetrievedChunk> graphChunks = queryGraph(question, enhanced, config, progress);
        List<RetrievedChunk> vectorChunks = queryVector(question, enhanced, config, progress);
        return fuseAndSupplement(question, graphChunks, vectorChunks, enhanced, config);
    }

    /**
     * 查询增强：重写 + HyDE 并行生成（提高召回）。
     */
    public EnhancedQuery enhance(String question, LlmConfig config, Consumer<String> progress) {
        notify(progress, "正在生成查询重写与 HyDE");
        CompletableFuture<String> rewriteFuture = CompletableFuture.supplyAsync(
                () -> llmService.call("rewrite", question, config));
        CompletableFuture<String> hydeFuture = CompletableFuture.supplyAsync(
                () -> llmService.call("hyde", question, config));
        String rewritten = rewriteFuture.join();
        String hyde = hydeFuture.join();
        log.debug("查询增强完成：rewritten={}, hydePresent={}", rewritten, hyde != null && !hyde.isBlank());
        return new EnhancedQuery(question, rewritten, hyde);
    }

    /**
     * 图谱检索：local（实体局部图）+ global（关系全局图）并行，提高召回。
     */
    public List<RetrievedChunk> queryGraph(String question, EnhancedQuery enhanced, LlmConfig config,
                                           Consumer<String> progress) {
        notify(progress, "正在执行图谱检索（local + global）");
        String graphQuery = enhanced.rewritten() != null ? enhanced.rewritten() : question;
        CompletableFuture<List<RetrievedChunk>> localFuture = CompletableFuture.supplyAsync(
                () -> queryPath(graphQuery, "local", "graph", progress));
        CompletableFuture<List<RetrievedChunk>> globalFuture = CompletableFuture.supplyAsync(
                () -> queryPath(graphQuery, "global", "graph", progress));
        List<RetrievedChunk> local = localFuture.join();
        List<RetrievedChunk> global = globalFuture.join();
        List<RetrievedChunk> merged = mergeChunks(local, global);
        notify(progress, String.format("图谱检索完成：命中 %d 条", merged.size()));
        return merged;
    }

    /**
     * 向量检索：多查询扩展（原问题 + 重写 + HyDE 三个变体并行），显著提高召回。
     */
    public List<RetrievedChunk> queryVector(String question, EnhancedQuery enhanced, LlmConfig config,
                                            Consumer<String> progress) {
        notify(progress, "正在执行向量检索（多查询扩展）");
        List<String> queries = new ArrayList<>();
        queries.add(question);
        if (enhanced.rewritten() != null && !enhanced.rewritten().isBlank()) {
            queries.add(enhanced.rewritten());
        }
        if (enhanced.hyde() != null && !enhanced.hyde().isBlank()) {
            queries.add(enhanced.hyde());
        }
        List<CompletableFuture<List<RetrievedChunk>>> futures = queries.stream()
                .map(q -> CompletableFuture.supplyAsync(() -> queryPath(q, "naive", "vector", progress)))
                .toList();
        List<RetrievedChunk> all = futures.stream().flatMap(f -> f.join().stream()).toList();
        List<RetrievedChunk> merged = mergeChunks(all);
        notify(progress, String.format("向量检索完成：命中 %d 条", merged.size()));
        return merged;
    }

    /**
     * RRF 融合 + ReRead 二次补全，产出最终结果。
     */
    public RetrievalResult fuseAndSupplement(String question, List<RetrievedChunk> graphChunks,
                                             List<RetrievedChunk> vectorChunks,
                                             EnhancedQuery enhanced, LlmConfig config) {
        List<RetrievedChunk> fused = fuse(graphChunks, vectorChunks);
        List<RetrievedChunk> supplemented = reread(question, fused, config);
        boolean degraded = enhanced.rewritten() == null && enhanced.hyde() == null;
        return new RetrievalResult(supplemented, enhanced,
                graphChunks.size(), vectorChunks.size(), degraded);
    }

    /** 合并多路片段（按 reference_id+content 去重，保留首现） */
    private List<RetrievedChunk> mergeChunks(List<RetrievedChunk>... sources) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (List<RetrievedChunk> source : sources) {
            for (RetrievedChunk chunk : source) {
                String key = chunk.referenceId() == null ? chunk.content() : chunk.referenceId();
                merged.putIfAbsent(key, chunk);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 推送进度回调（空回调时忽略） */
    private void notify(Consumer<String> progress, String message) {
        if (progress != null) {
            try {
                progress.accept(message);
            } catch (Exception e) {
                log.debug("进度回调异常：{}", e.getMessage());
            }
        }
    }

    /** 执行单路径检索（图谱/向量，流式查询实时回调进度） */
    private List<RetrievedChunk> queryPath(String query, String mode, String source, Consumer<String> progress) {
        try {
            List<Map<String, Object>> refs = lightRagClient.queryStream(query, mode, progress);
            List<RetrievedChunk> chunks = parseReferenceList(refs, source);
            log.debug("路径检索完成：mode={}, hits={}", mode, chunks.size());
            return chunks;
        } catch (Exception e) {
            log.warn("路径检索降级：mode={}, err={}", mode, e.getMessage());
            return List.of();
        }
    }

    /** 从 LightRAG 引用列表解析检索片段 */
    private List<RetrievedChunk> parseReferenceList(List<Map<String, Object>> refs, String source) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> ref : refs) {
            List<String> contents = extractContents(ref);
            if (contents.isEmpty()) {
                continue;
            }
            for (String content : contents) {
                chunks.add(new RetrievedChunk(
                        str(ref.get("reference_id")),
                        str(ref.get("file_path")),
                        content,
                        1.0 / (RRF_K + rank),
                        source));
            }
            rank++;
        }
        return chunks;
    }

    /** 从 LightRAG 响应中解析引用片段 */
    private List<RetrievedChunk> parseReferences(Map<String, Object> response, String source) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        Object refs = response.get("references");
        if (refs instanceof List<?> referenceList) {
            int rank = 1;
            for (Object ref : referenceList) {
                if (!(ref instanceof Map<?, ?> refMap)) {
                    continue;
                }
                List<String> contents = extractContents(refMap);
                if (contents.isEmpty()) {
                    continue;
                }
                for (String content : contents) {
                    chunks.add(new RetrievedChunk(
                            str(refMap.get("reference_id")),
                            str(refMap.get("file_path")),
                            content,
                            1.0 / (RRF_K + rank),
                            source));
                }
                rank++;
            }
        }
        return chunks;
    }

    /** 提取引用的片段内容列表 */
    private List<String> extractContents(Map<?, ?> refMap) {
        Object content = refMap.get("content");
        if (content instanceof List<?> contentList) {
            return contentList.stream().map(String::valueOf).toList();
        }
        if (content instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    /** RRF 倒数排名融合：图谱 + 向量 双路合并去重 */
    private List<RetrievedChunk> fuse(List<RetrievedChunk> graphChunks, List<RetrievedChunk> vectorChunks) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        mergePath(merged, graphChunks);
        mergePath(merged, vectorChunks);
        // 按融合得分降序
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(MAX_PER_PATH)
                .toList();
    }

    /** 将单路径结果并入融合表（同 ID 累加 RRF 得分） */
    private void mergePath(Map<String, RetrievedChunk> merged, List<RetrievedChunk> chunks) {
        int rank = 1;
        for (RetrievedChunk chunk : chunks) {
            double addScore = 1.0 / (RRF_K + rank);
            String key = chunk.referenceId() == null ? chunk.content() : chunk.referenceId();
            if (merged.containsKey(key)) {
                RetrievedChunk exist = merged.get(key);
                merged.put(key, new RetrievedChunk(exist.referenceId(), exist.filePath(),
                        exist.content(), exist.score() + addScore, exist.source() + "+" + chunk.source()));
            } else {
                merged.put(key, new RetrievedChunk(chunk.referenceId(), chunk.filePath(),
                        chunk.content(), addScore, chunk.source()));
            }
            rank++;
        }
    }

    /**
     * ReRead 二次检索：
     * 从已检索片段中抽取关键术语，用术语补查一次，将新片段合并进结果。
     */
    private List<RetrievedChunk> reread(String question, List<RetrievedChunk> fused, LlmConfig config) {
        if (fused.isEmpty()) {
            return fused;
        }
        // 抽取关键术语
        String summary = fused.stream().map(RetrievedChunk::content).reduce("", (a, b) -> a + "\n" + b);
        String terms = llmService.call("reread", summary, config);
        String termQuery = extractTerms(terms);
        if (termQuery == null) {
            return fused;
        }
        try {
            Map<String, Object> response = lightRagClient.query(termQuery, "mix", true);
            List<RetrievedChunk> extra = parseReferences(response, "reread");
            Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
            fused.forEach(c -> merged.put(c.referenceId(), c));
            extra.forEach(c -> merged.putIfAbsent(c.referenceId(), c));
            return merged.values().stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(MAX_PER_PATH)
                    .toList();
        } catch (Exception e) {
            log.warn("ReRead 二次检索降级：{}", e.getMessage());
            return fused;
        }
    }

    /** 从 LLM 输出的术语文本中提取顿号/逗号分隔的检索串 */
    private String extractTerms(String terms) {
        if (terms == null || terms.isBlank()) {
            return null;
        }
        String cleaned = terms.replace("\n", "、").trim();
        // 过滤说明性文字，仅保留含关键词的行
        String joined = String.join(" ", cleaned.split("[、,，；;]"))
                .trim();
        return joined.isBlank() ? null : joined;
    }

    /** 安全转字符串 */
    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}