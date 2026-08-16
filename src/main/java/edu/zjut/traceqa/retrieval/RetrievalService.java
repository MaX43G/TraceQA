package edu.zjut.traceqa.retrieval;

import jakarta.annotation.Resource;
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

    @Resource
    private LightRagClient lightRagClient;

    @Resource
    private LlmService llmService;

    /** RRF 融合常数 */
    private static final double RRF_K = 60.0;
    /** 每路保留的最大结果数 */
    private static final int MAX_PER_PATH = 12;

    

    /**
     * 查询增强：重写 + HyDE 并行生成（结合多轮历史消除指代，提高召回）。
     *
     * @param history 对话历史文本（可为空）
     */
    public EnhancedQuery enhance(String question, LlmConfig config, Consumer<String> progress, String history) {
        notify(progress, "正在生成查询重写与 HyDE");
        String rewriteInput = buildQueryInput(question, history);
        String hydeInput = buildQueryInput(question, history);
        CompletableFuture<String> rewriteFuture = CompletableFuture.supplyAsync(
                () -> llmService.call("rewrite", rewriteInput, config));
        CompletableFuture<String> hydeFuture = CompletableFuture.supplyAsync(
                () -> llmService.call("hyde", hydeInput, config));
        String rewritten = rewriteFuture.join();
        String hyde = hydeFuture.join();
        log.debug("查询增强完成：rewritten={}, hydePresent={}", rewritten, hyde != null && !hyde.isBlank());
        return new EnhancedQuery(question, rewritten, hyde);
    }

    /** 拼接对话历史与当前问题（无历史时仅当前问题） */
    private String buildQueryInput(String question, String history) {
        if (history == null || history.isBlank()) {
            return question;
        }
        return "对话历史：\n" + history + "当前问题：" + question;
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
        return new RetrievalResult(supplemented, degraded);
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

    /**
     * LLM 复杂度判定：是否需要聚合检索链路（图谱 + 向量复合检索）。
     *
     * <p>策略：先做零成本快速预检（极短的单点事实问题直接判定简单，跳过 LLM 调用），
     * 否则调用 LLM 按语义标准判定；LLM 失败/熔断时回退到规则判定（优雅降级）。</p>
     */
    public boolean isComplexQuery(String question, LlmConfig config) {
        // 快速预检：极短问题且无明显复杂信号 → 简单，省一次 LLM 调用
        if (question != null && question.length() <= 15 && !hasComplexSignal(question)) {
            return false;
        }
        // LLM 判定
        String result = llmService.call("complexity", question, config);
        if (result != null) {
            String upper = result.trim().toUpperCase();
            if (upper.contains("COMPLEX")) {
                return true;
            }
            if (upper.contains("SIMPLE")) {
                return false;
            }
        }
        // LLM 不可用：规则兜底
        return ruleComplex(question);
    }

    /** 规则判定：复杂逻辑词 / 多实体 / 长问题 */
    private boolean ruleComplex(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return hasComplexSignal(question) || question.trim().length() > 40;
    }

    /** 是否包含复杂信号（逻辑词或多实体分隔符） */
    private boolean hasComplexSignal(String question) {
        String[] complexWords = {
                "对比", "比较", "区别", "差异", "关系", "关联", "联系", "影响", "总结", "综述",
                "分析", "优缺点", "为什么", "如何选择", "vs", "VS", "versus", "相较于",
                "与", "和", "及", "或"
        };
        for (String word : complexWords) {
            if (question.contains(word)) {
                return true;
            }
        }
        return question.matches(".*[、，,;；].*");
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