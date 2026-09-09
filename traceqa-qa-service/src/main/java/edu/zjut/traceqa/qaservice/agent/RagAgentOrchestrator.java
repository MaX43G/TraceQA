package edu.zjut.traceqa.qaservice.agent;

import cn.hutool.crypto.SecureUtil;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.enums.IntentType;
import edu.zjut.traceqa.common.model.dto.ChatStreamRequest;
import edu.zjut.traceqa.common.model.dto.EnhancedQuery;
import edu.zjut.traceqa.common.model.dto.LlmConfig;
import edu.zjut.traceqa.common.model.dto.RetrievalResult;
import edu.zjut.traceqa.common.model.dto.RetrievedChunk;
import edu.zjut.traceqa.common.model.po.ChatMessage;
import edu.zjut.traceqa.common.model.po.ChatSession;
import edu.zjut.traceqa.common.model.vo.ReferenceVO;
import edu.zjut.traceqa.common.model.vo.ThinkingNodeVO;
import edu.zjut.traceqa.qaservice.retrieval.RetrievalService;
import edu.zjut.traceqa.qaservice.service.ChatService;
import edu.zjut.traceqa.qaservice.service.LlmService;
import edu.zjut.traceqa.qaservice.service.OpenAiCompatClient;
import edu.zjut.traceqa.qaservice.service.RedisCacheService;
import edu.zjut.traceqa.qaservice.sse.SsePublisher;
import edu.zjut.traceqa.qaservice.metrics.RagMetrics;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多 Agent 协同编排器。
 *
 * <p>实现「意图识别 -> 检索/搜索 -> 总结」的 Agent 工作流，并通过 SSE 实时推送
 * Agent 思考过程与流式回答：</p>
 * <pre>意图识别 → 查询重写/HyDE → 双路检索 → ReRead → 引用推送 → 总结生成</pre>
 *
 * <p>采用多层优雅降级：Alibaba Agent → ChatClient → 纯检索上下文 → 友好提示，
 * 任何一层失败均不会向用户抛出 500。</p>
 */
@Component
public class RagAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RagAgentOrchestrator.class);

    /**
     * 关键词检索作为首次查询补充：命中数达到该阈值时跳过关键词
     */
    private static final int KEYWORD_FALLBACK_THRESHOLD = 4;

    @Resource
    private ChatService chatService;
    @Resource
    private IntentAgent intentAgent;
    @Resource
    private AnswerAgent answerAgent;
    @Resource
    private RetrievalService retrievalService;
    @Resource
    private LlmService llmService;
    @Resource
    private SsePublisher ssePublisher;
    @Resource
    private RedisCacheService redisCacheService;
    @Resource
    private RagMetrics ragMetrics;

    /**
     * 并行检索时保护 thinking 节点列表与 SSE 进度推送的锁
     */
    private final Object thinkingLock = new Object();

    @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}")
    private String springAiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String springAiApiKey;

    /**
     * 流式执行完整 Agent 工作流（由 ragExecutor 线程调用）。
     *
     * @param cancelled 取消标志：用户中断/连接断开时置位，生成过程随即停止
     */
    public void streamChat(Long userId, ChatStreamRequest request, SseEmitter emitter, AtomicBoolean cancelled) {
        ragMetrics.queryStart();
        List<ThinkingNodeVO> thinking = new ArrayList<>();
        long start = System.currentTimeMillis();
        LlmConfig modelConfig = toLlmConfig(request);
        try {
            ChatSession session = chatService.getOrCreateSession(userId, request.getSessionId(),
                    request.getKnowledgeBaseId(), request.getContent());
            String history = chatService.buildHistoryText(session.getId(), 6);
            chatService.saveUserMessage(session.getId(), request.getContent());

            IntentType intent = recognizeIntent(emitter, thinking, request.getContent(), history, modelConfig);

            String answer;
            List<ReferenceVO> references = List.of();
            if (isDirectAnswer(intent)) {
                answer = respondDirect(emitter, thinking, request.getContent(), modelConfig, cancelled);
            } else {
                RetrievalResult result = retrieve(emitter, thinking, request.getContent(), history, modelConfig, cancelled);
                if (!result.hasContent()) {
                    List<RetrievedChunk> fallback = retrievalService.retryWithStrategy(request.getContent());
                    if (!fallback.isEmpty()) {
                        result = new RetrievalResult(fallback, true);
                    }
                }
                List<String> highlight = extractHighlightTerms(request.getContent());
                references = emitReferences(emitter, result, highlight);
                answer = generateAnswer(emitter, thinking, request.getContent(), history, result, modelConfig, cancelled);
            }

            persistAndFinish(session, thinking, references, answer, start, emitter);
            ssePublisher.complete(emitter);
        } catch (Exception e) {
            String trace = java.util.Arrays.stream(e.getStackTrace())
                    .limit(5)
                    .map(StackTraceElement::toString)
                    .collect(java.util.stream.Collectors.joining("\n  "));
            log.error("Agent 编排异常 [{}]: {}\n  {}", e.getClass().getSimpleName(), e.getMessage(), trace);
            markThinkingFailed(thinking);
            ssePublisher.completeWithError(emitter, Map.of(
                    "code", ErrorCode.LLM_UNAVAILABLE.getCode(),
                    "msg", "AI 服务暂时不可用，请稍后再试"));
        } finally {
            ragMetrics.queryEnd();
        }
    }

    /**
     * 从请求构造模型配置
     */
    private LlmConfig toLlmConfig(ChatStreamRequest request) {
        if (request.hasServerModel()) {
            String base = openAiCompatBaseUrl(springAiBaseUrl);
            return new LlmConfig(base, springAiApiKey, request.getServerModel());
        }
        if (request.hasCustomModel()) {
            LlmConfig config = new LlmConfig(request.getBaseUrl(), request.getApiKey(), request.getModel());
            return config.isValid() ? config : null;
        }
        return null;
    }

    private String openAiCompatBaseUrl(String springAiBase) {
        String base = springAiBase == null ? "" : springAiBase.trim();
        if (base.isBlank()) {
            return "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.contains("/v1")) {
            return base;
        }
        return base + "/v1";
    }

    /**
     * 意图识别节点（结果缓存 30 分钟）
     */
    private IntentType recognizeIntent(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                       String history, LlmConfig config) {
        ThinkingNodeVO node = startThinking(thinking, "意图识别", "intent-agent", "正在分析用户意图");
        ssePublisher.send(emitter, "thinking", node);
        String cacheKey = "intent:" + sha256(content);
        Optional<IntentType> cached = redisCacheService.get(cacheKey, IntentType.class);
        IntentType intent;
        if (cached.isPresent()) {
            intent = cached.get();
            ragMetrics.recordCacheHit();
            node.setData(Map.of("intentLabel", intent.getLabel(), "cached", true));
            finishThinking(thinking, emitter, "意图识别", "识别结果：" + intent.getLabel() + "（缓存命中）");
        } else {
            intent = intentAgent.identify(content, history, config);
            ragMetrics.recordCacheMiss();
            redisCacheService.put(cacheKey, intent, Duration.ofMinutes(30));
            ragMetrics.recordIntent(intent.name());
            node.setData(Map.of("intentLabel", intent.getLabel(), "cached", false));
            finishThinking(thinking, emitter, "意图识别", "识别结果：" + intent.getLabel());
        }
        return intent;
    }

    /**
     * 查询意图路由 + 三路检索 + ReRead + 精排节点
     */
    private RetrievalResult retrieve(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                     String history, LlmConfig config, AtomicBoolean cancelled) {
        long retrieveStart = System.currentTimeMillis();
        ThinkingNodeVO routerNode = startThinking(thinking, "检索策略调度", "router-agent",
                "正在由模型规划检索策略并选择检索工具");
        ssePublisher.send(emitter, "thinking", routerNode);
        RetrievalService.QueryType type = retrievalService.classifyQueryAgentic(content, config);
        String pathLabel = switch (type) {
            case DEFINITION -> "术语定义 → 关键词 + 向量检索（最快）";
            case COMPARE -> "对比问题 → 查询分解 + 聚合链路（图谱 + 向量 + 关键词）";
            case SIMPLE -> "简单问题 → 仅向量检索";
            case COMPLEX -> "复杂问题 → 聚合链路（图谱 + 向量 + 关键词）";
        };
        routerNode.setData(Map.of("strategy", type.name(), "pathLabel", pathLabel));
        finishThinking(thinking, emitter, "检索策略调度", pathLabel);

        if (type == RetrievalService.QueryType.DEFINITION) {
            EnhancedQuery simple = new EnhancedQuery(content, null, null);
            CompletableFuture<List<RetrievedChunk>> defVectorFuture = CompletableFuture.supplyAsync(
                    () -> runVector(emitter, thinking, content, simple, cancelled));
            CompletableFuture<List<RetrievedChunk>> defKeywordFuture = CompletableFuture.supplyAsync(
                    () -> runKeyword(emitter, thinking, content, config, cancelled));
            List<RetrievedChunk> vectorChunks = defVectorFuture.join();
            List<RetrievedChunk> keywordChunks = vectorChunks.size() < KEYWORD_FALLBACK_THRESHOLD
                    ? defKeywordFuture.join() : List.of();
            List<RetrievedChunk> fused = retrievalService.fuse(List.of(vectorChunks, keywordChunks));
            ThinkingNodeVO fuseNode = startThinking(thinking, "结果融合", "fusion-agent",
                    "正在融合关键词与向量结果");
            ssePublisher.send(emitter, "thinking", fuseNode);
            fuseNode.setData(Map.of(
                    "fusedCount", fused.size(),
                    "fusedSources", filePaths(fused)));
            finishThinking(thinking, emitter, "结果融合", "融合后共 " + fused.size() + " 条");
            emitRetrievalStats(emitter, 0, vectorChunks.size(), keywordChunks.size(), fused, retrieveStart);
            return new RetrievalResult(fused, true);
        }

        if (type == RetrievalService.QueryType.SIMPLE) {
            EnhancedQuery simple = new EnhancedQuery(content, null, null);
            List<RetrievedChunk> vectorChunks = runVector(emitter, thinking, content, simple, cancelled);
            emitRetrievalStats(emitter, 0, vectorChunks.size(), 0, vectorChunks, retrieveStart);
            return new RetrievalResult(vectorChunks, true);
        }

        ThinkingNodeVO enhanceNode = startThinking(thinking, "查询重写与 HyDE", "rewrite-agent",
                "正在生成查询重写与假设性文档");
        ssePublisher.send(emitter, "thinking", enhanceNode);
        EnhancedQuery enhanced = retrievalService.enhance(content, config,
                progress -> pushProgress(emitter, enhanceNode, cancelled, progress), history);
        String enhanceDetail = String.format("重写：%s", shortText(enhanced.getRewritten()));
        if (type == RetrievalService.QueryType.COMPARE && enhanced.getSubqueries() != null
                && !enhanced.getSubqueries().isEmpty()) {
            enhanceDetail += String.format("（分解 %d 个子问题）", enhanced.getSubqueries().size());
        }
        enhanceNode.setData(Map.of(
                "rewritten", enhanced.getRewritten() == null ? "" : enhanced.getRewritten(),
                "hyde", enhanced.getHyde() == null ? "" : enhanced.getHyde(),
                "subqueries", enhanced.getSubqueries() == null ? List.of() : enhanced.getSubqueries()));
        finishThinking(thinking, emitter, "查询重写与 HyDE", enhanceDetail);

        CompletableFuture<List<RetrievedChunk>> graphFuture = CompletableFuture.supplyAsync(() -> {
                    ThinkingNodeVO gNode = startThinking(thinking, "图谱检索", "graph-agent", "正在执行知识图谱检索");
                    ssePublisher.send(emitter, "thinking", gNode);
                    List<RetrievedChunk> chunks = retrievalService.queryGraph(content,
                            progress -> pushProgress(emitter, gNode, cancelled, progress));
                    gNode.setData(Map.of("hits", chunks.size(), "sources", filePaths(chunks)));
                    finishThinking(thinking, emitter, "图谱检索", "图谱命中 " + chunks.size() + " 条");
                    return chunks;
                });
        CompletableFuture<List<RetrievedChunk>> vectorFuture = CompletableFuture.supplyAsync(
                        () -> runVector(emitter, thinking, content, enhanced, cancelled));
        CompletableFuture<List<RetrievedChunk>> keywordFuture = CompletableFuture.supplyAsync(
                        () -> runKeyword(emitter, thinking, content, config, cancelled));
        List<RetrievedChunk> graphChunks = graphFuture.join();
        List<RetrievedChunk> vectorChunks = vectorFuture.join();
        List<RetrievedChunk> keywordChunks = keywordFuture.join();

        // 1) 结果融合
        ThinkingNodeVO fuseNode = startThinking(thinking, "结果融合", "fusion-agent", "正在融合三路检索结果");
        ssePublisher.send(emitter, "thinking", fuseNode);
        List<RetrievedChunk> fused = retrievalService.fuse(List.of(graphChunks, vectorChunks, keywordChunks));
        fuseNode.setData(Map.of(
                "graphCount", graphChunks.size(),
                "vectorCount", vectorChunks.size(),
                "keywordCount", keywordChunks.size(),
                "fusedCount", fused.size(),
                "fusedSources", filePaths(fused)));
        finishThinking(thinking, emitter, "结果融合", "融合后共 " + fused.size() + " 条");

        // 2) 二次检索补全（可选，默认关闭）
        if (retrievalService.isRereadEnabled()) {
            ThinkingNodeVO supNode = startThinking(thinking, "二次检索补全", "reread-agent", "正在基于关键要素二次检索补全");
            ssePublisher.send(emitter, "thinking", supNode);
            fused = retrievalService.supplement(fused);
            finishThinking(thinking, emitter, "二次检索补全", "补全后共 " + fused.size() + " 条");
        }

        // 3) 结果精排（可选，默认关闭）
        if (retrievalService.isRerankEnabled()) {
            ThinkingNodeVO rkNode = startThinking(thinking, "结果精排", "rerank-agent", "正在语义重排检索结果");
            ssePublisher.send(emitter, "thinking", rkNode);
            fused = retrievalService.rerank(content, fused);
            finishThinking(thinking, emitter, "结果精排", "排序完成");
        }

        boolean degraded = enhanced.getRewritten() == null && enhanced.getHyde() == null;
        RetrievalResult result = new RetrievalResult(fused, degraded);
        emitRetrievalStats(emitter, graphChunks.size(), vectorChunks.size(), keywordChunks.size(),
                result.getChunks(), retrieveStart);
        return result;
    }

    /**
     * 推送「检索分析」数据（三路命中数 + 来源文档分布 + 耗时）
     */
    private void emitRetrievalStats(SseEmitter emitter, int graphHits, int vectorHits, int keywordHits,
                                    List<RetrievedChunk> fused, long startMs) {
        Map<String, Integer> sourceDocs = new LinkedHashMap<>();
        for (RetrievedChunk c : fused) {
            if (c.getFilePath() != null && !c.getFilePath().isBlank()) {
                String file = extractFilename(c.getFilePath());
                sourceDocs.merge(file, 1, Integer::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("graphHits", graphHits);
        stats.put("vectorHits", vectorHits);
        stats.put("keywordHits", keywordHits);
        stats.put("fusedCount", fused.size());
        stats.put("elapsedMs", System.currentTimeMillis() - startMs);
        stats.put("sourceDocs", sourceDocs);
        ragMetrics.recordRetrievalHits("graph", graphHits);
        ragMetrics.recordRetrievalHits("vector", vectorHits);
        ragMetrics.recordRetrievalHits("keyword", keywordHits);
        ssePublisher.send(emitter, "stats", stats);
    }

    /**
     * 向量检索节点
     */
    private List<RetrievedChunk> runVector(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                           EnhancedQuery enhanced, AtomicBoolean cancelled) {
        ThinkingNodeVO vectorNode = startThinking(thinking, "向量检索", "vector-agent", "正在执行向量语义检索");
        ssePublisher.send(emitter, "thinking", vectorNode);
        List<RetrievedChunk> chunks = retrievalService.queryVector(content, enhanced,
                progress -> pushProgress(emitter, vectorNode, cancelled, progress));
        vectorNode.setData(Map.of("hits", chunks.size(), "sources", filePaths(chunks)));
        finishThinking(thinking, emitter, "向量检索", "向量命中 " + chunks.size() + " 条");
        return chunks;
    }

    /**
     * 关键词检索节点
     */
    private List<RetrievedChunk> runKeyword(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                            LlmConfig config, AtomicBoolean cancelled) {
        ThinkingNodeVO kwNode = startThinking(thinking, "关键词检索", "keyword-agent", "正在执行关键词检索");
        ssePublisher.send(emitter, "thinking", kwNode);
        List<RetrievedChunk> chunks = retrievalService.queryKeyword(content, config,
                progress -> pushProgress(emitter, kwNode, cancelled, progress));
        kwNode.setData(Map.of("hits", chunks.size(), "sources", filePaths(chunks)));
        finishThinking(thinking, emitter, "关键词检索", "关键词命中 " + chunks.size() + " 条");
        return chunks;
    }

    /**
     * 推送检索过程进度（取消时停止推送）
     */
    private void pushProgress(SseEmitter emitter, ThinkingNodeVO node, AtomicBoolean cancelled, String progress) {
        if (cancelled != null && cancelled.get()) {
            return;
        }
        ssePublisher.send(emitter, "thinking",
                new ThinkingNodeVO(node.getStage(), node.getAgent(), "running", progress, null));
    }

    private String shortText(String text) {
        if (text == null) {
            return "无";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 30 ? oneLine.substring(0, 30) + "…" : oneLine;
    }

    /**
     * 推送引用来源事件
     */
    private List<ReferenceVO> emitReferences(SseEmitter emitter, RetrievalResult result, List<String> highlight) {
        List<ReferenceVO> references = buildReferences(result, highlight);
        ssePublisher.send(emitter, "references", Map.of("references", references));
        return references;
    }

    /**
     * 流式生成回答：Alibaba Agent 优先，ChatClient 兜底
     */
    private String generateAnswer(SseEmitter emitter, List<ThinkingNodeVO> thinking,
                                  String content, String history, RetrievalResult result, LlmConfig config,
                                  AtomicBoolean cancelled) {
        ThinkingNodeVO node = startThinking(thinking, "总结生成", "answer-agent", "正在生成回答");
        ssePublisher.send(emitter, "thinking", node);

        String prompt = buildAnswerPrompt(content, history, result);
        String answer = streamAnswer(emitter, prompt, config, cancelled);
        if (answer.isBlank()) {
            answer = degradedAnswer(result);
            ragMetrics.recordDegraded();
            ssePublisher.send(emitter, "delta", Map.of("content", answer));
        }
        String modelName = config != null && config.getModel() != null ? config.getModel() : "平台默认";
        node.setData(Map.of("model", modelName, "promptLength", prompt.length()));
        finishThinking(thinking, emitter, "总结生成", "回答生成完毕（模型：" + modelName + "）");
        return answer;
    }

    /**
     * 寒暄/系统咨询：直接应答，不进入检索链路
     */
    private String respondDirect(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                 LlmConfig config, AtomicBoolean cancelled) {
        ThinkingNodeVO node = startThinking(thinking, "直接应答", "answer-agent", "无需检索，直接应答");
        ssePublisher.send(emitter, "thinking", node);
        String answer = consumeWithReasoning(emitter, llmService.callStreamWithReasoning("chat", content, config), cancelled);
        if (answer.isBlank()) {
            answer = "您好！我是「溯知」，可以为你解答《数据挖掘》课程相关问题，"
                    + "也可以询问平台的使用方式。请描述你的问题。";
            ssePublisher.send(emitter, "delta", Map.of("content", answer));
        }
        finishThinking(thinking, emitter, "直接应答", "应答完成");
        return answer;
    }

    private String streamAnswer(SseEmitter emitter, String prompt, LlmConfig config, AtomicBoolean cancelled) {
        StringBuilder acc = new StringBuilder();
        acc.append(consumeWithReasoning(emitter, llmService.callStreamWithReasoning("summary", prompt, config), cancelled));
        if (acc.isEmpty()) {
            acc.append(consume(emitter, answerAgent.streamAnswer(prompt, config), cancelled));
        }
        return acc.toString();
    }

    /**
     * 消费内容块流并逐块推送 delta 事件（支持用户中断）
     */
    private String consume(SseEmitter emitter, Flux<String> flux, AtomicBoolean cancelled) {
        StringBuilder acc = new StringBuilder();
        flux.takeWhile(_ -> !cancelled.get()).toIterable().forEach(chunk -> {
            acc.append(chunk);
            ssePublisher.send(emitter, "delta", Map.of("content", chunk));
        });
        return acc.toString();
    }

    /**
     * 消费含推理过程的内容块流：content → delta 事件，reasoning_content → reasoning 事件
     */
    private String consumeWithReasoning(SseEmitter emitter, Flux<OpenAiCompatClient.DeltaChunk> flux,
                                        AtomicBoolean cancelled) {
        StringBuilder acc = new StringBuilder();
        flux.takeWhile(_ -> !cancelled.get()).toIterable().forEach(chunk -> {
            if (!chunk.reasoningContent().isEmpty()) {
                ssePublisher.send(emitter, "reasoning", Map.of("content", chunk.reasoningContent()));
            }
            if (!chunk.content().isEmpty()) {
                acc.append(chunk.content());
                ssePublisher.send(emitter, "delta", Map.of("content", chunk.content()));
            }
        });
        return acc.toString();
    }

    /**
     * 持久化 AI 消息并推送 done 事件
     */
    private void persistAndFinish(ChatSession session, List<ThinkingNodeVO> thinking,
                                  List<ReferenceVO> references, String answer,
                                  long start, SseEmitter emitter) {
        long latency = System.currentTimeMillis() - start;
        if (answer == null || answer.isBlank()) {
            ragMetrics.recordQueryLatency(latency, "unknown");
            log.info("回答为空（可能被中断），不保存 AI 消息：session={}", session.getId());
            ssePublisher.send(emitter, "done", Map.of(
                    "sessionId", session.getId(),
                    "title", session.getTitle()));
            return;
        }
        try {
            ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer,
                    thinking, references, latency);
            ssePublisher.send(emitter, "done", Map.of(
                    "sessionId", session.getId(),
                    "messageId", assistant.getId(),
                    "title", session.getTitle()));
            ragMetrics.recordQueryLatency(latency, "success");
            log.info("问答完成：session={}, latency={}ms", session.getId(), latency);
        } catch (Exception e) {
            log.warn("持久化消息失败（thinking trace 序列化异常？），降级保存：{}", e.getMessage());
            try {
                ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer,
                        List.of(), references, latency);
                ssePublisher.send(emitter, "done", Map.of(
                        "sessionId", session.getId(),
                        "messageId", assistant.getId(),
                        "title", session.getTitle()));
                log.info("问答完成（降级保存，thinking trace 已丢弃）：session={}", session.getId());
            } catch (Exception ex) {
                log.error("降级保存也失败：{}", ex.getMessage());
                ssePublisher.send(emitter, "done", Map.of(
                        "sessionId", session.getId(),
                        "title", session.getTitle()));
            }
        }
    }

    /**
     * 检索为空或生成失败时的纯检索降级回答
     */
    private String degradedAnswer(RetrievalResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ AI 服务暂时不可用，已降级为「纯检索模式」，以下为检索到的原始资料：\n\n");
        if (result == null || !result.hasContent()) {
            sb.append("未检索到相关资料，请尝试更换提问方式。");
            return sb.toString();
        }
        int idx = 1;
        for (RetrievedChunk chunk : result.getChunks()) {
            sb.append("**片段 ").append(idx).append("** [citation:").append(idx).append("]\n\n")
                    .append(chunk.getContent()).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    /**
     * 组装「问题 + 上下文」总结提示词
     */
    private String buildAnswerPrompt(String question, String history, RetrievalResult result) {
        StringBuilder sb = new StringBuilder();
        if (history != null && !history.isBlank()) {
            sb.append("【对话历史】\n").append(history).append("\n");
        }
        sb.append("【用户问题】\n").append(question).append("\n\n【检索上下文】\n");
        if (result == null || !result.hasContent()) {
            sb.append("（未检索到相关上下文，请如实告知用户资料库中暂无相关内容）");
            return sb.toString();
        }
        int idx = 1;
        for (RetrievedChunk chunk : result.getChunks()) {
            sb.append("[citation:").append(idx).append("] ")
                    .append(chunk.getContent()).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    /**
     * 组装引用来源列表
     */
    private List<ReferenceVO> buildReferences(RetrievalResult result, List<String> highlight) {
        if (result == null || !result.hasContent()) {
            return List.of();
        }
        List<ReferenceVO> refs = new ArrayList<>();
        int idx = 1;
        for (RetrievedChunk chunk : result.getChunks()) {
            refs.add(new ReferenceVO(idx, extractFilename(chunk.getFilePath()), chunk.getFilePath(),
                    chunk.getContent(), chunk.getHeadings(), highlight));
            idx++;
        }
        return refs;
    }

    /**
     * 从用户问题提取用于片段内高亮的术语
     */
    private List<String> extractHighlightTerms(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        String[] parts = question.split("[\\s，。；、？！：:（）()\"'“”‘’\\[\\]{}<>《》—…~`]+");
        for (String part : parts) {
            String t = part.trim();
            if (t.length() >= 2 && t.length() <= 12 && !terms.contains(t)) {
                terms.add(t);
            }
            if (terms.size() >= 8) {
                break;
            }
        }
        return terms;
    }

    /**
     * 从文件路径提取文件名
     */
    private String extractFilename(String path) {
        if (path == null || !path.contains("/")) {
            return path == null ? "未知来源" : path;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 提取片段列表中不重复的文件路径（最多 max 条）
     */
    private List<String> filePaths(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (RetrievedChunk c : chunks) {
            if (c.getFilePath() != null && !c.getFilePath().isBlank()) {
                seen.add(extractFilename(c.getFilePath()));
                if (seen.size() >= 20) break;
            }
        }
        return List.copyOf(seen);
    }

    /**
     * 判断是否为无需检索的直接应答意图
     */
    private boolean isDirectAnswer(IntentType intent) {
        return intent == IntentType.GREETING || intent == IntentType.SYSTEM_QUESTION;
    }

    /**
     * 创建思考节点（加入追踪列表，不推送）
     */
    private ThinkingNodeVO startThinking(List<ThinkingNodeVO> thinking, String stage,
                                         String agent, String message) {
        ThinkingNodeVO node = new ThinkingNodeVO(stage, agent, "running", message, null);
        node.setStartMillis(System.currentTimeMillis());
        synchronized (thinkingLock) {
            thinking.add(node);
        }
        return node;
    }

    /**
     * 完成思考节点并推送
     */
    private void finishThinking(List<ThinkingNodeVO> thinking, SseEmitter emitter, String stage, String detail) {
        synchronized (thinkingLock) {
            for (int i = thinking.size() - 1; i >= 0; i--) {
                ThinkingNodeVO node = thinking.get(i);
                if (node.getStage().equals(stage) && "running".equals(node.getStatus())) {
                    node.setStatus("done");
                    node.setDetail(detail);
                    node.setCostMs(System.currentTimeMillis() - node.getStartMillis());
                    ssePublisher.send(emitter, "thinking", node);
                    return;
                }
            }
        }
    }

    /**
     * 异常时将未完成节点标记为失败
     */
    private void markThinkingFailed(List<ThinkingNodeVO> thinking) {
        synchronized (thinkingLock) {
            for (int i = thinking.size() - 1; i >= 0; i--) {
                ThinkingNodeVO node = thinking.get(i);
                if ("running".equals(node.getStatus())) {
                    node.setStatus("failed");
                    node.setDetail("执行失败，已降级");
                    node.setCostMs(System.currentTimeMillis() - node.getStartMillis());
                    break;
                }
            }
        }
    }

    /**
     * SHA-256 摘要（缓存 key 用）
     */
    private String sha256(String text) {
        return SecureUtil.sha256(text);
    }
}