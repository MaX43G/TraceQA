package edu.zjut.traceqa.retrieval;

import jakarta.annotation.Resource;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.model.dto.EnhancedQuery;
import edu.zjut.traceqa.model.dto.LlmConfig;
import edu.zjut.traceqa.model.dto.RetrievalResult;
import edu.zjut.traceqa.model.dto.RetrievedChunk;
import edu.zjut.traceqa.service.LlmService;
import edu.zjut.traceqa.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import cn.hutool.crypto.SecureUtil;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三路混合检索与融合服务。
 *
 * <p>实现完整的 RAG 增强检索链路：</p>
 * <ol>
 *   <li><b>查询增强（Query Enhancement）</b>：查询重写 + HyDE + 对比类问题分解；</li>
 *   <li><b>三路检索</b>：图谱（local+global）、向量（多查询 naive）、关键词（hl_keywords）；</li>
 *   <li><b>RRF 融合</b>：三路按倒数排名融合并去重；</li>
 *   <li><b>ReRead 二次补全 + LLM 精排</b>：关键术语补查 + 相关性重排。</li>
 * </ol>
 *
 * <p>任一路径失败均自动降级，绝不中断整个链路。检索/决策结果经 Redis 缓存
 * （短 TTL），Redis 不可用时自动降级为无缓存。</p>
 */
@Slf4j
@Service
public class RetrievalService {

    @Resource
    private LightRagClient lightRagClient;

    @Resource
    private LlmService llmService;

    @Resource
    private RedisCacheService redisCacheService;

    /** RRF 融合常数 */
    private static final double RRF_K = 60.0;
    /** 每路保留的最大结果数 */
    private static final int MAX_PER_PATH = 12;

    /**
     * 查询增强：重写 + HyDE 并行生成（结合多轮历史消除指代，提高召回），
     * 并对「对比/比较」类问题做子问题分解。结果缓存 10 分钟。
     *
     * @param history 对话历史文本（可为空）
     */
    public EnhancedQuery enhance(String question, LlmConfig config, Consumer<String> progress, String history) {
        String cacheKey = "enhance:" + sha256(question + "|" + history);
        Optional<EnhancedQuery> cached = redisCacheService.get(cacheKey, EnhancedQuery.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        notify(progress, "正在生成查询重写与 HyDE");
        String rewriteInput = buildQueryInput(question, history);
        String hydeInput = buildQueryInput(question, history);
        CompletableFuture<String> rewriteFuture = CompletableFuture.supplyAsync(
                () -> llmService.call("rewrite", rewriteInput, config));
        CompletableFuture<String> hydeFuture = CompletableFuture.supplyAsync(
                () -> llmService.call("hyde", hydeInput, config));
        String rewritten = rewriteFuture.join();
        String hyde = hydeFuture.join();
        List<String> subs = decomposeSubqueries(question);
        EnhancedQuery result = new EnhancedQuery(question, rewritten, hyde, subs);
        redisCacheService.put(cacheKey, result, Duration.ofMinutes(10));
        log.debug("查询增强完成：rewritten={}, hydePresent={}, subqueries={}",
                rewritten, hyde != null && !hyde.isBlank(), subs);
        return result;
    }

    /** 拼接对话历史与当前问题（无历史时仅当前问题） */
    private String buildQueryInput(String question, String history) {
        if (history == null || history.isBlank()) {
            return question;
        }
        return "对话历史：\n" + history + "当前问题：" + question;
    }

    /** 对比/比较类问题按连接词拆分为子问题（用于多查询向量检索） */
    private List<String> decomposeSubqueries(String question) {
        if (question == null
                || (!question.contains("对比") && !question.contains("比较") && !question.contains("区别")
                && !question.contains("vs") && !question.contains("VS"))) {
            return List.of();
        }
        List<String> subs = new ArrayList<>();
        for (String part : question.split("与|和|以及|vs|VS|相较于|对比|比较|区别")) {
            String t = part.trim();
            if (t.length() >= 2 && !t.isBlank()) {
                subs.add(t);
            }
        }
        return subs.stream().limit(2).toList();
    }

    /**
     * 图谱检索：local（实体局部图）+ global（关系全局图）并行，提高召回。
     * 结果缓存 10 分钟。
     */
    public List<RetrievedChunk> queryGraph(String question, EnhancedQuery enhanced, LlmConfig config,
                                           Consumer<String> progress) {
        notify(progress, "正在执行图谱检索（local + global）");
        String graphQuery = enhanced.getRewritten() != null ? enhanced.getRewritten() : question;
        String cacheKey = "graph:" + sha256(graphQuery);
        Optional<List<RetrievedChunk>> cached = redisCacheService.get(cacheKey, new TypeReference<>() {
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        CompletableFuture<List<RetrievedChunk>> localFuture = CompletableFuture.supplyAsync(
                () -> queryPath(graphQuery, "local", "graph", progress));
        CompletableFuture<List<RetrievedChunk>> globalFuture = CompletableFuture.supplyAsync(
                () -> queryPath(graphQuery, "global", "graph", progress));
        List<RetrievedChunk> local = localFuture.join();
        List<RetrievedChunk> global = globalFuture.join();
        List<RetrievedChunk> merged = mergeChunks(local, global);
        redisCacheService.put(cacheKey, merged, Duration.ofMinutes(10));
        notify(progress, String.format("图谱检索完成：命中 %d 条", merged.size()));
        return merged;
    }

    /**
     * 向量检索：多查询扩展（原问题 + 重写 + HyDE + 分解子问题并行），显著提高召回。
     * 结果缓存 5 分钟。
     */
    public List<RetrievedChunk> queryVector(String question, EnhancedQuery enhanced, LlmConfig config,
                                            Consumer<String> progress) {
        notify(progress, "正在执行向量检索（多查询扩展）");
        List<String> queries = new ArrayList<>();
        queries.add(question);
        if (enhanced.getRewritten() != null && !enhanced.getRewritten().isBlank()) {
            queries.add(enhanced.getRewritten());
        }
        if (enhanced.getHyde() != null && !enhanced.getHyde().isBlank()) {
            queries.add(enhanced.getHyde());
        }
        if (enhanced.getSubqueries() != null) {
            queries.addAll(enhanced.getSubqueries());
        }
        String cacheKey = "vec:" + sha256(String.join("|", queries));
        Optional<List<RetrievedChunk>> cached = redisCacheService.get(cacheKey, new TypeReference<>() {
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        List<CompletableFuture<List<RetrievedChunk>>> futures = queries.stream()
                .map(q -> CompletableFuture.supplyAsync(() -> queryPath(q, "naive", "vector", progress)))
                .toList();
        List<RetrievedChunk> all = futures.stream().flatMap(f -> f.join().stream()).toList();
        List<RetrievedChunk> merged = mergeChunks(all);
        redisCacheService.put(cacheKey, merged, Duration.ofMinutes(5));
        notify(progress, String.format("向量检索完成：命中 %d 条", merged.size()));
        return merged;
    }

    /**
     * 关键词检索：提取关键术语，以 hl_keywords 方式检索（对术语/编号类问题召回更准）。
     * 结果缓存 5 分钟。
     */
    public List<RetrievedChunk> queryKeyword(String question, EnhancedQuery enhanced, LlmConfig config,
                                             Consumer<String> progress) {
        notify(progress, "正在执行关键词检索");
        String cacheKey = "kw:" + sha256(question);
        Optional<List<RetrievedChunk>> cached = redisCacheService.get(cacheKey, new TypeReference<>() {
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        List<String> keywords = extractKeywords(question, config);
        List<RetrievedChunk> chunks;
        if (keywords.isEmpty()) {
            chunks = List.of();
        } else {
            List<Map<String, Object>> refs = lightRagClient.queryStream(question, "naive", keywords, progress);
            chunks = parseReferenceList(refs, "keyword");
        }
        redisCacheService.put(cacheKey, chunks, Duration.ofMinutes(5));
        notify(progress, String.format("关键词检索完成：命中 %d 条", chunks.size()));
        return chunks;
    }

    /** 提取检索关键词：LLM 优先，失败回退英文 token 规则 */
    private List<String> extractKeywords(String question, LlmConfig config) {
        try {
            String raw = llmService.call("keyword", question, config);
            if (raw != null && !raw.isBlank()) {
                List<String> kw = Arrays.stream(raw.split("[、，,；;\\n]"))
                        .map(String::trim)
                        .filter(s -> s.length() >= 2 && !s.isBlank())
                        .limit(6)
                        .toList();
                if (!kw.isEmpty()) {
                    return kw;
                }
            }
        } catch (Exception e) {
            log.debug("关键词提取降级：{}", e.getMessage());
        }
        return extractKeywordsFallback(question);
    }

    /** 关键词规则兜底：提取英文 token */
    private List<String> extractKeywordsFallback(String question) {
        if (question == null) {
            return List.of();
        }
        List<String> kw = new ArrayList<>();
        Matcher m = Pattern.compile("[A-Za-z][A-Za-z0-9+.#-]{1,}").matcher(question);
        while (m.find() && kw.size() < 5) {
            kw.add(m.group());
        }
        return kw;
    }

    /**
     * 三路 RRF 融合 + ReRead 二次补全 + LLM 精排，产出最终结果。
     */
    public RetrievalResult fuseAndSupplement(String question, List<RetrievedChunk> graphChunks,
                                             List<RetrievedChunk> vectorChunks, List<RetrievedChunk> keywordChunks,
                                             EnhancedQuery enhanced, LlmConfig config) {
        List<RetrievedChunk> fused = fuse(graphChunks, vectorChunks, keywordChunks);
        List<RetrievedChunk> supplemented = reread(question, fused, config);
        List<RetrievedChunk> reranked = rerank(question, supplemented, config);
        boolean degraded = enhanced.getRewritten() == null && enhanced.getHyde() == null;
        return new RetrievalResult(reranked, degraded);
    }

    /** 合并多路片段（按 reference_id+content 去重，保留首现） */
    private List<RetrievedChunk> mergeChunks(List<RetrievedChunk>... sources) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (List<RetrievedChunk> source : sources) {
            for (RetrievedChunk chunk : source) {
                String key = chunk.getReferenceId() == null ? chunk.getContent() : chunk.getReferenceId();
                merged.putIfAbsent(key, chunk);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * LLM 复杂度判定：是否需要聚合检索链路（图谱 + 向量 + 关键词复合检索）。
     *
     * <p>策略：先做零成本快速预检（极短的单点事实问题直接判定简单，跳过 LLM 调用），
     * 否则调用 LLM 按语义标准判定；LLM 失败/熔断时回退到规则判定（优雅降级）。
     * 判定结果缓存 30 分钟。</p>
     */
    public boolean isComplexQuery(String question, LlmConfig config) {
        if (question == null) {
            return false;
        }
        String cacheKey = "complex:" + sha256(question);
        Optional<Boolean> cached = redisCacheService.get(cacheKey, Boolean.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        boolean result;
        // 快速预检：极短问题且无明显复杂信号 → 简单，省一次 LLM 调用
        if (question.length() <= 15 && !hasComplexSignal(question)) {
            result = false;
        } else {
            // LLM 判定
            String llmResult = llmService.call("complexity", question, config);
            if (llmResult != null) {
                String upper = llmResult.trim().toUpperCase();
                if (upper.contains("COMPLEX")) {
                    result = true;
                } else if (upper.contains("SIMPLE")) {
                    result = false;
                } else {
                    result = ruleComplex(question);
                }
            } else {
                result = ruleComplex(question);
            }
        }
        redisCacheService.put(cacheKey, result, Duration.ofMinutes(30));
        return result;
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
            List<Map<String, Object>> refs = lightRagClient.queryStream(query, mode, null, progress);
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

    /** RRF 倒数排名融合：图谱 + 向量 + 关键词 三路合并去重 */
    private List<RetrievedChunk> fuse(List<RetrievedChunk>... sources) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (List<RetrievedChunk> source : sources) {
            mergePath(merged, source);
        }
        // 按融合得分降序
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(MAX_PER_PATH)
                .toList();
    }

    /** 将单路径结果并入融合表（同 ID 累加 RRF 得分） */
    private void mergePath(Map<String, RetrievedChunk> merged, List<RetrievedChunk> chunks) {
        int rank = 1;
        for (RetrievedChunk chunk : chunks) {
            double addScore = 1.0 / (RRF_K + rank);
            String key = chunk.getReferenceId() == null ? chunk.getContent() : chunk.getReferenceId();
            if (merged.containsKey(key)) {
                RetrievedChunk exist = merged.get(key);
                merged.put(key, new RetrievedChunk(exist.getReferenceId(), exist.getFilePath(),
                        exist.getContent(), exist.getScore() + addScore, exist.getSource() + "+" + chunk.getSource()));
            } else {
                merged.put(key, new RetrievedChunk(chunk.getReferenceId(), chunk.getFilePath(),
                        chunk.getContent(), addScore, chunk.getSource()));
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
        String summary = fused.stream().map(RetrievedChunk::getContent).reduce("", (a, b) -> a + "\n" + b);
        String terms = llmService.call("reread", summary, config);
        String termQuery = extractTerms(terms);
        if (termQuery == null) {
            return fused;
        }
        try {
            Map<String, Object> response = lightRagClient.query(termQuery, "mix", true);
            List<RetrievedChunk> extra = parseReferences(response, "reread");
            Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
            fused.forEach(c -> merged.put(c.getReferenceId(), c));
            extra.forEach(c -> merged.putIfAbsent(c.getReferenceId(), c));
            return merged.values().stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(MAX_PER_PATH)
                    .toList();
        } catch (Exception e) {
            log.warn("ReRead 二次检索降级：{}", e.getMessage());
            return fused;
        }
    }

    /**
     * LLM 精排：让模型从结果片段中筛除无关项并按相关度重排（失败时返回原序）。
     */
    private List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks, LlmConfig config) {
        if (chunks == null || chunks.size() <= 3) {
            return chunks;
        }
        try {
            StringBuilder sb = new StringBuilder("问题：").append(question).append("\n\n片段列表：\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(shorten(chunks.get(i).getContent())).append("\n\n");
            }
            String result = llmService.call("rerank", sb.toString(), config);
            if (result == null || result.isBlank()) {
                return chunks;
            }
            List<RetrievedChunk> reranked = new ArrayList<>();
            for (String token : result.split("[，,;；\\s]+")) {
                try {
                    int idx = Integer.parseInt(token.trim());
                    if (idx >= 1 && idx <= chunks.size() && !reranked.contains(chunks.get(idx - 1))) {
                        reranked.add(chunks.get(idx - 1));
                    }
                } catch (NumberFormatException ignored) {
                    // 忽略非数字 token
                }
            }
            return reranked.isEmpty() ? chunks : reranked;
        } catch (Exception e) {
            log.debug("LLM 精排降级：{}", e.getMessage());
            return chunks;
        }
    }

    /** 截断片段文本（精排提示词用，控制 token） */
    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    /** 从 LLM 输出的术语文本中提取顿号/逗号分隔的检索串 */
    private String extractTerms(String terms) {
        if (terms == null || terms.isBlank()) {
            return null;
        }
        String cleaned = terms.replace("\n", "、").trim();
        String joined = String.join(" ", cleaned.split("[、,，；;]"))
                .trim();
        return joined.isBlank() ? null : joined;
    }

    /** SHA-256 摘要（缓存 key 用，Hutool） */
    private String sha256(String text) {
        return SecureUtil.sha256(text);
    }

    /** 安全转字符串 */
    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
