package edu.zjut.traceqa.qaservice.agent;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.model.dto.EnhancedQuery;
import edu.zjut.traceqa.common.model.dto.LlmConfig;
import edu.zjut.traceqa.common.model.dto.ManualStreamRequest;
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
import edu.zjut.traceqa.qaservice.sse.SsePublisher;
import edu.zjut.traceqa.qaservice.metrics.RagMetrics;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手动检索模式处理器。
 *
 * <p>独立于 {@link RagAgentOrchestrator}，不经过 Agent 策略调度，
 * 直接根据用户选择的检索方式（HyDE/向量/关键词/图谱）执行检索并生成回答。</p>
 */
@Component
public class ManualRetrievalHandler {

    private static final Logger log = LoggerFactory.getLogger(ManualRetrievalHandler.class);

    private static final int MAX_CONTEXT_CHUNKS = 8;

    @Resource
    private ChatService chatService;
    @Resource
    private RetrievalService retrievalService;
    @Resource
    private LlmService llmService;
    @Resource
    private SsePublisher ssePublisher;
    @Resource
    private RagMetrics ragMetrics;

    @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}")
    private String springAiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String springAiApiKey;

    /**
     * 手动模式 SSE 流式执行入口
     */
    public void streamChat(Long userId, ManualStreamRequest request, SseEmitter emitter, AtomicBoolean cancelled) {
        ragMetrics.queryStart();
        List<ThinkingNodeVO> thinking = new ArrayList<>();
        long start = System.currentTimeMillis();
        LlmConfig modelConfig = toLlmConfig(request);
        try {
            ChatSession session = chatService.getOrCreateSession(userId, request.getSessionId(),
                    request.getKnowledgeBaseId(), request.getContent());
            String history = chatService.buildHistoryText(session.getId(), 6);
            chatService.saveUserMessage(session.getId(), request.getContent());

            // 策略标签
            String pathLabel = buildPathLabel(request);
            ThinkingNodeVO routerNode = startThinking(thinking, "手动检索", "manual-agent", pathLabel);
            ssePublisher.send(emitter, "thinking", routerNode);
            routerNode.setData(Map.of("strategy", "MANUAL", "pathLabel", pathLabel, "selected", request.getStrategies()));
            finishThinking(thinking, emitter, "手动检索", pathLabel);

            RetrievalResult result = retrieve(emitter, thinking, request, modelConfig, cancelled);

            if (!result.hasContent()) {
                List<RetrievedChunk> fallback = retrievalService.retryWithStrategy(request.getContent());
                if (!fallback.isEmpty()) {
                    result = new RetrievalResult(fallback, true);
                }
            }

            List<String> highlight = extractHighlightTerms(request.getContent());
            List<ReferenceVO> references = emitReferences(emitter, result, highlight);
            String[] answerResult = generateAnswer(emitter, thinking, request.getContent(), history, result, modelConfig, cancelled);
            String answer = answerResult[0];
            String reasoningContent = answerResult[1];

            persistAndFinish(session, thinking, references, answer, reasoningContent, start, emitter);
            ssePublisher.complete(emitter);
        } catch (Exception e) {
            String trace = java.util.Arrays.stream(e.getStackTrace())
                    .limit(5)
                    .map(StackTraceElement::toString)
                    .collect(java.util.stream.Collectors.joining("\n  "));
            log.error("手动检索编排异常 [{}]: {}\n  {}", e.getClass().getSimpleName(), e.getMessage(), trace);
            markThinkingFailed(thinking);
            ssePublisher.completeWithError(emitter, Map.of(
                    "code", ErrorCode.LLM_UNAVAILABLE.getCode(),
                    "msg", "AI 服务暂时不可用，请稍后再试"));
        } finally {
            ragMetrics.queryEnd();
        }
    }

    // ========== 检索 ==========

    private RetrievalResult retrieve(SseEmitter emitter, List<ThinkingNodeVO> thinking,
                                     ManualStreamRequest request,
                                     LlmConfig config, AtomicBoolean cancelled) {
        long retrieveStart = System.currentTimeMillis();
        String content = request.getContent();

        boolean useHyde = request.useHyde();
        boolean useVector = request.useVector();
        boolean useKeyword = request.useKeyword();
        boolean useGraph = request.useGraph();

        // HyDE 生成
        EnhancedQuery enhanced = new EnhancedQuery(content, null, null);
        if (useHyde) {
            ThinkingNodeVO hydeNode = startThinking(thinking, "假设性文档生成", "hyde-agent", "正在生成假设性文档");
            ssePublisher.send(emitter, "thinking", hydeNode);
            enhanced = retrievalService.enhance(content, config,
                    progress -> pushProgress(emitter, hydeNode, cancelled, progress), null);
            String detail = String.format("重写：%s", shortText(enhanced.getRewritten()));
            hydeNode.setData(Map.of(
                    "rewritten", enhanced.getRewritten() == null ? "" : enhanced.getRewritten(),
                    "hyde", enhanced.getHyde() == null ? "" : enhanced.getHyde()));
            finishThinking(thinking, emitter, "假设性文档生成", detail);
        }

        // 并行执行选中的检索
        List<CompletableFuture<List<RetrievedChunk>>> futures = new ArrayList<>();
        List<String> futureLabels = new ArrayList<>();

        if (useGraph) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                ThinkingNodeVO gNode = startThinking(thinking, "图谱检索", "graph-agent", "正在执行知识图谱检索");
                ssePublisher.send(emitter, "thinking", gNode);
                List<RetrievedChunk> chunks = retrievalService.queryGraph(content,
                        progress -> pushProgress(emitter, gNode, cancelled, progress));
                gNode.setData(Map.of("hits", chunks.size(), "sources", filePaths(chunks)));
                finishThinking(thinking, emitter, "图谱检索", "图谱命中 " + chunks.size() + " 条");
                return chunks;
            }));
            futureLabels.add("graph");
        }
        if (useVector) {
            EnhancedQuery q = enhanced;
            futures.add(CompletableFuture.supplyAsync(
                    () -> runVector(emitter, thinking, content, q, cancelled)));
            futureLabels.add("vector");
        }
        if (useKeyword) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> runKeyword(emitter, thinking, content, config, cancelled)));
            futureLabels.add("keyword");
        }

        List<List<RetrievedChunk>> allResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        int graphHits = 0, vectorHits = 0, keywordHits = 0;
        for (int i = 0; i < futureLabels.size(); i++) {
            int count = allResults.get(i).size();
            switch (futureLabels.get(i)) {
                case "graph" -> graphHits = count;
                case "vector" -> vectorHits = count;
                case "keyword" -> keywordHits = count;
            }
        }

        // RRF 融合
        ThinkingNodeVO fuseNode = startThinking(thinking, "结果融合", "fusion-agent", "正在融合检索结果");
        ssePublisher.send(emitter, "thinking", fuseNode);
        List<RetrievedChunk> fused = retrievalService.fuse(allResults);
        fuseNode.setData(Map.of(
                "graphCount", graphHits, "vectorCount", vectorHits, "keywordCount", keywordHits,
                "fusedCount", fused.size(), "fusedSources", filePaths(fused)));
        finishThinking(thinking, emitter, "结果融合", "融合后共 " + fused.size() + " 条");

        if (retrievalService.isRereadEnabled()) {
            ThinkingNodeVO supNode = startThinking(thinking, "二次检索补全", "reread-agent", "正在基于关键要素二次检索补全");
            ssePublisher.send(emitter, "thinking", supNode);
            fused = retrievalService.supplement(fused);
            finishThinking(thinking, emitter, "二次检索补全", "补全后共 " + fused.size() + " 条");
        }

        if (retrievalService.isRerankEnabled()) {
            ThinkingNodeVO rkNode = startThinking(thinking, "结果精排", "rerank-agent", "正在语义重排检索结果");
            ssePublisher.send(emitter, "thinking", rkNode);
            fused = retrievalService.rerank(content, fused);
            finishThinking(thinking, emitter, "结果精排", "排序完成");
        }

        RetrievalResult result = new RetrievalResult(fused, false);
        emitRetrievalStats(emitter, graphHits, vectorHits, keywordHits, result.getChunks(), retrieveStart);
        return result;
    }

    // ========== 检索节点 ==========

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

    // ========== 生成回答 ==========

    private String[] generateAnswer(SseEmitter emitter, List<ThinkingNodeVO> thinking,
                                  String content, String history, RetrievalResult result,
                                  LlmConfig config, AtomicBoolean cancelled) {
        ThinkingNodeVO node = startThinking(thinking, "总结生成", "answer-agent", "正在生成回答");
        ssePublisher.send(emitter, "thinking", node);

        String prompt = buildAnswerPrompt(content, history, result);
        String[] answerAndReasoning = consumeWithReasoning(emitter, llmService.callStreamWithReasoning("summary", prompt, config), cancelled);
        String answer = answerAndReasoning[0];
        String reasoningContent = answerAndReasoning[1];
        if (answer.isBlank()) {
            answer = degradedAnswer(result);
            ragMetrics.recordDegraded();
            ssePublisher.send(emitter, "delta", Map.of("content", answer));
        }
        String modelName = config != null && config.getModel() != null ? config.getModel() : "平台默认";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", modelName);
        data.put("promptLength", prompt.length());
        data.put("prompt", prompt);
        node.setData(data);
        finishThinking(thinking, emitter, "总结生成", "回答生成完毕（模型：" + modelName + "）");
        return new String[]{answer, reasoningContent};
    }

    // ========== 工具方法（与 RagAgentOrchestrator 一致） ==========

    private LlmConfig toLlmConfig(ManualStreamRequest request) {
        if (request.hasServerModel()) {
            String base = openAiCompatBaseUrl(springAiBaseUrl);
            return new LlmConfig(base, springAiApiKey, request.getServerModel());
        }
        if (request.hasCustomModel()) {
            return new LlmConfig(request.getBaseUrl(), request.getApiKey(), request.getModel());
        }
        String base = openAiCompatBaseUrl(springAiBaseUrl);
        return new LlmConfig(base, springAiApiKey, null);
    }

    private String openAiCompatBaseUrl(String baseUrl) {
        if (baseUrl == null) return null;
        return baseUrl.replaceAll("/v1/?$", "/");
    }

    private String buildPathLabel(ManualStreamRequest request) {
        StringBuilder sb = new StringBuilder("手动模式 → ");
        if (request.useHyde()) sb.append("HyDE + ");
        if (request.useVector()) sb.append("向量 + ");
        if (request.useKeyword()) sb.append("关键词 + ");
        if (request.useGraph()) sb.append("图谱 + ");
        return sb.length() > 7 ? sb.substring(0, sb.length() - 3) : "手动模式";
    }

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
            if (idx > MAX_CONTEXT_CHUNKS) break;
            sb.append("[citation:").append(idx).append("] ").append(chunk.getContent()).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

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

    private List<ReferenceVO> emitReferences(SseEmitter emitter, RetrievalResult result, List<String> highlight) {
        List<ReferenceVO> references = buildReferences(result, highlight);
        ssePublisher.send(emitter, "references", Map.of("references", references));
        return references;
    }

    private List<ReferenceVO> buildReferences(RetrievalResult result, List<String> highlight) {
        if (result == null || !result.hasContent()) return List.of();
        List<ReferenceVO> refs = new ArrayList<>();
        int idx = 1;
        for (RetrievedChunk chunk : result.getChunks()) {
            refs.add(new ReferenceVO(idx, extractFilename(chunk.getFilePath()), chunk.getFilePath(),
                    chunk.getContent(), chunk.getHeadings(), highlight));
            idx++;
        }
        return refs;
    }

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

    private String extractFilename(String path) {
        if (path == null || path.isBlank()) return "";
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return name.length() > 40 ? name.substring(0, 40) + "…" : name;
    }

    private String shortText(String text) {
        if (text == null || text.isBlank()) return "（空）";
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 30 ? oneLine.substring(0, 30) + "…" : oneLine;
    }

    private List<String> extractHighlightTerms(String question) {
        if (question == null || question.isBlank()) return List.of();
        return java.util.Arrays.stream(question.split("[\\s,，。？?！!、；;：:（()）\\[\\]【】\"']+"))
                .filter(w -> w.length() >= 2)
                .limit(6)
                .toList();
    }

    private void persistAndFinish(ChatSession session, List<ThinkingNodeVO> thinking,
                                  List<ReferenceVO> references, String answer,
                                  String reasoningContent, long start, SseEmitter emitter) {
        long latency = System.currentTimeMillis() - start;
        if (answer == null || answer.isBlank()) {
            ragMetrics.recordQueryLatency(latency, "unknown");
            ssePublisher.send(emitter, "done", Map.of("sessionId", session.getId(), "title", session.getTitle()));
            return;
        }
        try {
            ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer, thinking, references, reasoningContent, latency);
            ssePublisher.send(emitter, "done", Map.of(
                    "sessionId", session.getId(), "messageId", assistant.getId(), "title", session.getTitle()));
            ragMetrics.recordQueryLatency(latency, "success");
        } catch (Exception e) {
            log.warn("持久化消息失败：{}", e.getMessage());
            try {
                ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer, List.of(), references, reasoningContent, latency);
                ssePublisher.send(emitter, "done", Map.of(
                        "sessionId", session.getId(), "messageId", assistant.getId(), "title", session.getTitle()));
            } catch (Exception ex) {
                ssePublisher.send(emitter, "done", Map.of("sessionId", session.getId(), "title", session.getTitle()));
            }
        }
    }

    private void markThinkingFailed(List<ThinkingNodeVO> thinking) {
        synchronized (thinking) {
            for (ThinkingNodeVO node : thinking) {
                if ("running".equals(node.getStatus())) {
                    node.setStatus("failed");
                }
            }
        }
    }

    private String[] consumeWithReasoning(SseEmitter emitter, Flux<OpenAiCompatClient.DeltaChunk> flux, AtomicBoolean cancelled) {
        StringBuilder acc = new StringBuilder();
        StringBuilder reasoningAcc = new StringBuilder();
        flux.takeWhile(_ -> !cancelled.get()).toIterable().forEach(chunk -> {
            if (!chunk.reasoningContent().isEmpty()) {
                reasoningAcc.append(chunk.reasoningContent());
                ssePublisher.send(emitter, "reasoning", Map.of("content", chunk.reasoningContent()));
            }
            if (!chunk.content().isEmpty()) {
                acc.append(chunk.content());
                ssePublisher.send(emitter, "delta", Map.of("content", chunk.content()));
            }
        });
        return new String[]{acc.toString(), reasoningAcc.toString()};
    }

    private ThinkingNodeVO startThinking(List<ThinkingNodeVO> thinking, String stage, String agent, String message) {
        ThinkingNodeVO node = new ThinkingNodeVO(stage, agent, "running", message, null);
        synchronized (thinking) {
            thinking.add(node);
        }
        return node;
    }

    private void finishThinking(List<ThinkingNodeVO> thinking, SseEmitter emitter, String stage, String summary) {
        synchronized (thinking) {
            for (int i = thinking.size() - 1; i >= 0; i--) {
                ThinkingNodeVO node = thinking.get(i);
                if (stage.equals(node.getStage()) && "running".equals(node.getStatus())) {
                    node.setStatus("done");
                    node.setMessage(summary);
                    break;
                }
            }
        }
        ssePublisher.send(emitter, "thinking", new ThinkingNodeVO(stage, null, "done", summary, null));
    }

    private void pushProgress(SseEmitter emitter, ThinkingNodeVO node, AtomicBoolean cancelled, String progress) {
        if (cancelled != null && cancelled.get()) return;
        ssePublisher.send(emitter, "thinking", new ThinkingNodeVO(node.getStage(), node.getAgent(), "running", progress, null));
    }

    private java.util.List<String> filePaths(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(RetrievedChunk::getFilePath)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }
}
