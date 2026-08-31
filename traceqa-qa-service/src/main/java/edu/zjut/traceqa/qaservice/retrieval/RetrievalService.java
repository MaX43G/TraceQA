package edu.zjut.traceqa.qaservice.retrieval;

import cn.hutool.crypto.SecureUtil;
import edu.zjut.traceqa.common.config.LightRagClient;
import edu.zjut.traceqa.common.model.dto.EnhancedQuery;
import edu.zjut.traceqa.common.model.dto.LlmConfig;
import edu.zjut.traceqa.common.model.dto.RetrievalResult;
import edu.zjut.traceqa.common.model.dto.RetrievedChunk;
import edu.zjut.traceqa.qaservice.config.RerankClient;
import edu.zjut.traceqa.qaservice.service.LlmService;
import edu.zjut.traceqa.qaservice.service.RedisCacheService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 检索增强服务。
 *
 * <p>实现 Agentic 检索策略：复杂度/策略判定、查询重写与 HyDE、查询分解、
 * 图谱(local/global)与向量(多查询)并行检索、关键词检索、RRF 融合、
 * ReRead 补全、语义重排与 LLM 精排。查询/决策结果经 Redis 短 TTL 缓存。</p>
 */
@Service
public class RetrievalService {

    private static final double RRF_K = 60.0;
    private static final int MAX_PER_PATH = 12;

    private final LightRagClient lightRagClient;
    private final LlmService llmService;
    private final RedisCacheService redisCacheService;
    private final RerankClient rerankClient;

    public RetrievalService(LightRagClient lightRagClient, LlmService llmService,
                            RedisCacheService redisCacheService, RerankClient rerankClient) {
        this.lightRagClient = lightRagClient;
        this.llmService = llmService;
        this.redisCacheService = redisCacheService;
        this.rerankClient = rerankClient;
    }

    /**
     * 查询类型
     */
    public enum QueryType {
        /**
         * 术语/概念定义 → 仅关键词 + 向量
         */
        DEFINITION,
        /**
         * 对比/比较 → 查询分解 + 全链路
         */
        COMPARE,
        /**
         * 一般简单 → 仅向量
         */
        SIMPLE,
        /**
         * 一般复杂 → 全链路
         */
        COMPLEX
    }

    /**
     * 查询增强（重写 + HyDE + 子问题分解），10 分钟缓存
     */
    public EnhancedQuery enhance(String question, LlmConfig config, Consumer<String> progress, String history) {
        String key = "enhance:" + sha256(question);
        var cached = redisCacheService.get(key, EnhancedQuery.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        CompletableFuture<String> rewriteFuture = CompletableFuture.supplyAsync(() ->
                llmService.call("rewrite", history == null || history.isBlank()
                        ? question : "对话历史：\n" + history + "\n当前问题：" + question, config));
        CompletableFuture<String> hydeFuture = CompletableFuture.supplyAsync(() ->
                llmService.call("hyde", question, config));
        String rewritten = rewriteFuture.join();
        String hyde = hydeFuture.join();
        List<String> subqueries = decomposeSubqueries(question);
        EnhancedQuery query = new EnhancedQuery(question, rewritten, hyde, subqueries);
        redisCacheService.put(key, query, Duration.ofMinutes(10));
        if (progress != null) {
            progress.accept("重写：" + shortText(rewritten));
        }
        return query;
    }

    /**
     * 图谱检索（local + global 并行），10 分钟缓存
     */
    public List<RetrievedChunk> queryGraph(String question, Consumer<String> progress) {
        String key = "graph:" + sha256(question);
        var cached = redisCacheService.get(key, new tools.jackson.core.type.TypeReference<List<RetrievedChunk>>() {
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        CompletableFuture<List<RetrievedChunk>> local = CompletableFuture.supplyAsync(() ->
                queryPath(question, "local", "graph", progress));
        CompletableFuture<List<RetrievedChunk>> global = CompletableFuture.supplyAsync(() ->
                queryPath(question, "global", "graph", progress));
        List<RetrievedChunk> result = mergeChunks(List.of(local.join(), global.join()));
        redisCacheService.put(key, result, Duration.ofMinutes(10));
        return result;
    }

    /**
     * 向量检索（多查询：原问题 + 重写 + HyDE + 子问题），5 分钟缓存
     */
    public List<RetrievedChunk> queryVector(String question, EnhancedQuery enhanced, Consumer<String> progress) {
        String key = "vec:" + sha256(question);
        var cached = redisCacheService.get(key, new tools.jackson.core.type.TypeReference<List<RetrievedChunk>>() {
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        List<String> queries = new ArrayList<>();
        queries.add(question);
        if (enhanced != null) {
            if (enhanced.getRewritten() != null && !enhanced.getRewritten().isBlank()) {
                queries.add(enhanced.getRewritten());
            }
            if (enhanced.getHyde() != null && !enhanced.getHyde().isBlank()) {
                queries.add(enhanced.getHyde());
            }
            if (enhanced.getSubqueries() != null) {
                queries.addAll(enhanced.getSubqueries());
            }
        }
        List<List<RetrievedChunk>> paths = new ArrayList<>();
        for (String q : queries) {
            paths.add(queryPath(q, "naive", "vector", progress));
        }
        List<RetrievedChunk> result = mergeChunks(paths);
        redisCacheService.put(key, result, Duration.ofMinutes(5));
        return result;
    }

    /**
     * 关键词检索（提取关键词后流式检索），5 分钟缓存
     */
    public List<RetrievedChunk> queryKeyword(String question, LlmConfig config, Consumer<String> progress) {
        String key = "kw:" + sha256(question);
        var cached = redisCacheService.get(key, new tools.jackson.core.type.TypeReference<List<RetrievedChunk>>() {
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        List<String> keywords = extractKeywords(question, config);
        List<RetrievedChunk> result = parseReferences(
                lightRagClient.queryStream(question, "naive", keywords, progress), "keyword");
        redisCacheService.put(key, result, Duration.ofMinutes(5));
        return result;
    }

    /**
     * 混合模式兜底重试
     */
    public List<RetrievedChunk> retryWithStrategy(String question) {
        Map<String, Object> data = lightRagClient.query(question, "hybrid", true);
        return parseReferences(extractReferences(data), "retry");
    }

    /**
     * 融合 + ReRead + 精排
     */
    public RetrievalResult fuseAndSupplement(String question, List<RetrievedChunk> graphChunks,
                                             List<RetrievedChunk> vectorChunks, List<RetrievedChunk> keywordChunks,
                                             EnhancedQuery enhanced, LlmConfig config) {
        List<RetrievedChunk> fused = fuse(List.of(graphChunks, vectorChunks, keywordChunks));
        fused = reread(fused, config);
        fused = rerankWithModel(question, fused, config);
        boolean degraded = enhanced == null
                || (enhanced.getRewritten() == null && enhanced.getHyde() == null);
        return new RetrievalResult(fused, degraded);
    }

    /**
     * 多路结果 RRF 融合去重
     */
    public final List<RetrievedChunk> fuse(List<List<RetrievedChunk>> sources) {
        return mergeChunks(sources);
    }

    /**
     * 复杂度预检（<=15 字符且无复杂信号视为简单）
     */
    public boolean isComplexQuery(String question, LlmConfig config) {
        if (question == null || question.trim().length() <= 15 && !hasComplexSignal(question)) {
            return false;
        }
        String key = "complex:" + sha256(question);
        var cached = redisCacheService.get(key, String.class);
        if (cached.isPresent()) {
            return "COMPLEX".equals(cached.get());
        }
        String result = llmService.call("complexity", question, config);
        if (result == null) {
            return ruleComplex(question);
        }
        boolean complex = result.toUpperCase().contains("COMPLEX");
        redisCacheService.put(key, complex ? "COMPLEX" : "SIMPLE", Duration.ofMinutes(30));
        return complex;
    }

    /**
     * 规则优先的查询类型判定
     */
    public QueryType classifyQuery(String question, LlmConfig config) {
        if (isCompareQuestion(question)) {
            return QueryType.COMPARE;
        }
        if (isDefinitionQuestion(question)) {
            return QueryType.DEFINITION;
        }
        return isComplexQuery(question, config) ? QueryType.COMPLEX : QueryType.SIMPLE;
    }

    /**
     * Agentic 检索策略判定，30 分钟缓存
     */
    public QueryType classifyQueryAgentic(String question, LlmConfig config) {
        String key = "agentic:" + sha256(question);
        var cached = redisCacheService.get(key, String.class);
        if (cached.isPresent()) {
            return parseQueryType(cached.get());
        }
        String result = llmService.call("agentic", question, config);
        if (result == null) {
            return classifyQuery(question, config);
        }
        QueryType type = parseQueryType(result);
        redisCacheService.put(key, type.name(), Duration.ofMinutes(30));
        return type;
    }

    private QueryType parseQueryType(String raw) {
        String up = raw == null ? "" : raw.toUpperCase();
        if (up.contains("COMPLEX")) {
            return QueryType.COMPLEX;
        }
        if (up.contains("DEFINITION")) {
            return QueryType.DEFINITION;
        }
        return QueryType.SIMPLE;
    }

    private List<RetrievedChunk> queryPath(String query, String mode, String source, Consumer<String> progress) {
        List<Map<String, Object>> refs = lightRagClient.queryStream(query, mode, null, progress);
        return parseReferences(refs, source);
    }

    private List<RetrievedChunk> parseReferences(List<Map<String, Object>> refs, String source) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        if (refs == null) {
            return chunks;
        }
        int rank = 0;
        for (Map<String, Object> ref : refs) {
            rank++;
            String referenceId = str(ref.get("reference_id"));
            String filePath = str(ref.get("file_path"));
            String content = extractContents(ref);
            List<String> headings = extractHeadings(ref);
            chunks.add(new RetrievedChunk(referenceId, filePath, content, 1.0 / (RRF_K + rank), source, headings));
        }
        return chunks;
    }

    private List<RetrievedChunk> mergeChunks(List<List<RetrievedChunk>> sources) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (List<RetrievedChunk> list : sources) {
            if (list == null) {
                continue;
            }
            for (RetrievedChunk chunk : list) {
                String id = chunk.getReferenceId() != null && !chunk.getReferenceId().isBlank()
                        ? chunk.getReferenceId() : chunk.getContent();
                RetrievedChunk existing = merged.get(id);
                if (existing == null) {
                    merged.put(id, chunk);
                } else {
                    existing.setScore(existing.getScore() + chunk.getScore());
                    existing.setSource(mergePath(existing.getSource(), chunk.getSource()));
                }
            }
        }
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(MAX_PER_PATH)
                .toList();
    }

    private String mergePath(String a, String b) {
        Set<String> parts = new HashSet<>();
        if (a != null) {
            parts.addAll(Arrays.asList(a.split("\\+")));
        }
        if (b != null) {
            parts.addAll(Arrays.asList(b.split("\\+")));
        }
        parts.remove("");
        return String.join("+", parts);
    }

    private List<RetrievedChunk> reread(List<RetrievedChunk> fused, LlmConfig config) {
        if (fused.isEmpty()) {
            return fused;
        }
        String summary = fused.stream().map(RetrievedChunk::getContent)
                .collect(Collectors.joining("\n"));
        String raw = llmService.call("reread", summary, config);
        List<String> terms = extractTerms(raw);
        if (terms.isEmpty()) {
            return fused;
        }
        String termQuery = String.join(" ", terms);
        try {
            Map<String, Object> data = lightRagClient.query(termQuery, "hybrid", true);
            List<RetrievedChunk> extra = parseReferences(extractReferences(data), "reread");
            return mergeChunks(List.of(fused, extra));
        } catch (Exception e) {
            return fused;
        }
    }

    private List<RetrievedChunk> rerankWithModel(String question, List<RetrievedChunk> chunks, LlmConfig config) {
        if (chunks.size() <= 3) {
            return chunks;
        }
        List<Integer> order;
        List<String> docs = chunks.stream().map(RetrievedChunk::getContent).toList();
        order = rerankClient.rerank(question, docs);
        if (order == null) {
            order = llmRerank(question, chunks, config);
        }
        if (order == null) {
            return chunks;
        }
        List<RetrievedChunk> reranked = new ArrayList<>();
        for (int idx : order) {
            if (idx >= 0 && idx < chunks.size() && !reranked.contains(chunks.get(idx))) {
                reranked.add(chunks.get(idx));
            }
        }
        for (RetrievedChunk chunk : chunks) {
            if (!reranked.contains(chunk)) {
                reranked.add(chunk);
            }
        }
        return reranked;
    }

    private List<Integer> llmRerank(String question, List<RetrievedChunk> chunks, LlmConfig config) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i).append("] ").append(chunks.get(i).getContent()).append("\n");
        }
        String raw = llmService.call("rerank", "问题：" + question + "\n片段：\n" + sb, config);
        if (raw == null) {
            return null;
        }
        List<Integer> order = new ArrayList<>();
        for (String token : raw.split("[,\\s]+")) {
            try {
                order.add(Integer.parseInt(token.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return order.isEmpty() ? null : order;
    }

    private List<String> decomposeSubqueries(String question) {
        if (!isCompareQuestion(question)) {
            return List.of();
        }
        String[] connectors = {"与", "和", "对比", "比较", "vs", "versus", "区别", "or"};
        for (String connector : connectors) {
            int idx = question.indexOf(connector);
            if (idx > 0) {
                String left = question.substring(0, idx).trim();
                String right = question.substring(idx + connector.length()).trim();
                if (left.length() >= 3 && right.length() >= 3) {
                    return List.of(left, right);
                }
            }
        }
        return List.of();
    }

    private List<String> extractKeywords(String question, LlmConfig config) {
        String raw = llmService.call("keyword", question, config);
        if (raw != null && !raw.isBlank()) {
            List<String> keywords = Arrays.stream(raw.split("[、，,；;\\n]"))
                    .map(String::trim)
                    .filter(k -> !k.isEmpty())
                    .limit(6)
                    .toList();
            if (!keywords.isEmpty()) {
                return keywords;
            }
        }
        return extractKeywordsFallback(question);
    }

    private List<String> extractKeywordsFallback(String question) {
        List<String> keywords = new ArrayList<>();
        for (String token : question.split("[^a-zA-Z0-9\\u4e00-\\u9fa5]+")) {
            if (token.length() >= 2 && token.length() <= 12) {
                keywords.add(token);
            }
            if (keywords.size() >= 6) {
                break;
            }
        }
        return keywords;
    }

    private List<String> extractTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[、，,；;\\n]"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .limit(6)
                .toList();
    }

    private boolean ruleComplex(String question) {
        return hasComplexSignal(question);
    }

    private boolean hasComplexSignal(String question) {
        String lower = question == null ? "" : question.toLowerCase();
        String[] logicWords = {"为什么", "如何", "区别", "对比", "关系", "影响", "总结", "归纳", "流程", "原理", "综合", "以及"};
        for (String word : logicWords) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return Pattern.compile("[，,、；;？?]").matcher(question == null ? "" : question).find();
    }

    private boolean isCompareQuestion(String question) {
        String lower = question == null ? "" : question.toLowerCase();
        return lower.contains("对比") || lower.contains("比较") || lower.contains("区别")
                || lower.contains(" vs ") || lower.contains(" versus ");
    }

    private boolean isDefinitionQuestion(String question) {
        String lower = question == null ? "" : question.toLowerCase();
        return lower.startsWith("什么是") || lower.startsWith("什么是 ") || lower.startsWith("定义")
                || lower.startsWith("概念") || lower.contains("是什么意思");
    }

    private String extractContents(Map<String, Object> ref) {
        Object content = ref.get("content");
        if (content instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining("\n"));
        }
        return content == null ? "" : String.valueOf(content);
    }

    private List<String> extractHeadings(Map<String, Object> ref) {
        Object headings = ref.get("content_headings");
        if (!(headings instanceof List<?> list) || list.isEmpty()) {
            headings = ref.get("headings");
        }
        if (headings instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> extractReferences(Map<String, Object> data) {
        Object refs = data.get("references");
        if (refs instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(r -> (Map<String, Object>) r)
                    .toList();
        }
        return List.of();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String shortText(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 30 ? text.substring(0, 30) + "…" : text;
    }

    private String sha256(String text) {
        return SecureUtil.sha256(text);
    }
}