package edu.zjut.traceqa.agent;

import jakarta.annotation.Resource;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.enums.IntentType;
import edu.zjut.traceqa.model.dto.LlmConfig;
import edu.zjut.traceqa.model.dto.ChatStreamRequest;
import edu.zjut.traceqa.model.vo.ReferenceVO;
import edu.zjut.traceqa.model.vo.ThinkingNodeVO;
import edu.zjut.traceqa.model.po.ChatMessage;
import edu.zjut.traceqa.model.po.ChatSession;
import edu.zjut.traceqa.model.dto.EnhancedQuery;
import edu.zjut.traceqa.model.dto.RetrievedChunk;
import edu.zjut.traceqa.model.dto.RetrievalResult;
import edu.zjut.traceqa.retrieval.RetrievalService;
import edu.zjut.traceqa.service.ChatService;
import edu.zjut.traceqa.service.LlmService;
import edu.zjut.traceqa.service.RedisCacheService;
import edu.zjut.traceqa.sse.SsePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import cn.hutool.crypto.SecureUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多 Agent 协同编排器。
 *
 * <p>实现「意图识别 -> 检索/搜索 -> 总结」的 Agent 工作流，并通过 SSE 实时推送
 * Agent 思考过程与流式回答：</p>
 * <pre>
 *  意图识别 → 查询重写/HyDE → 双路检索 → ReRead → 引用推送 → 总结生成
 * </pre>
 *
 * <p>采用多层优雅降级：Alibaba Agent → ChatClient → 纯检索上下文 → 友好提示，
 * 任何一层失败均不会向用户抛出 500。</p>
 */
@Slf4j
@Component
public class RagAgentOrchestrator {

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

    /** Spring AI 默认 Base URL 与 API Key（服务端模型切换时使用） */
    @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}")
    private String springAiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String springAiApiKey;

    

    /**
     * 流式执行完整 Agent 工作流（由 ragExecutor 线程调用）。
     *
     * @param cancelled 取消标志：用户中断/连接断开时置位，生成过程随即停止
     */
    public void streamChat(Long userId, ChatStreamRequest request, SseEmitter emitter,
                           AtomicBoolean cancelled) {
        List<ThinkingNodeVO> thinking = new ArrayList<>();
        long start = System.currentTimeMillis();
        // 解析自定义模型配置（仅本次请求使用，不持久化）
        LlmConfig modelConfig = toLlmConfig(request);
        try {
            // 1. 获取/创建会话；先取历史（不含当前消息），再保存当前用户消息
            ChatSession session = chatService.getOrCreateSession(userId, request.getSessionId(),
                    request.getKnowledgeBaseId(), request.getContent());
            String history = chatService.buildHistoryText(session.getId(), 6);
            chatService.saveUserMessage(session.getId(), request.getContent());

            // 2. 意图识别（带多轮历史）
            IntentType intent = recognizeIntent(emitter, thinking, request.getContent(), history, modelConfig);

            // 3. 课程问答走 RAG 检索链路，否则直接应答
            String answer;
            List<ReferenceVO> references = List.of();
            if (isDirectAnswer(intent)) {
                answer = respondDirect(emitter, thinking, request.getContent(), modelConfig, cancelled);
            } else {
                RetrievalResult result = retrieve(emitter, thinking, request.getContent(), history, modelConfig, cancelled);
                // 未检索到内容 → 换混合模式(mix)兜底重试一次；仍为空则照常回答（如实说明未检索到）
                if (result == null || !result.hasContent()) {
                    List<RetrievedChunk> fallback = retrievalService.retryWithStrategy(request.getContent());
                    if (!fallback.isEmpty()) {
                        result = new RetrievalResult(fallback, true);
                    }
                }
                List<String> highlight = extractHighlightTerms(request.getContent());
                references = emitReferences(emitter, result, highlight);
                answer = generateAnswer(emitter, thinking, request.getContent(), history, result, modelConfig, cancelled);
            }

            // 4. 持久化并结束（必须关闭 SSE 连接，否则前端 onEnd 不触发）
            persistAndFinish(session, thinking, references, answer, start, emitter);
            ssePublisher.complete(emitter);
        } catch (Exception e) {
            log.error("Agent 编排异常，整体降级：{}", e.getMessage());
            markThinkingFailed(thinking);
            ssePublisher.completeWithError(emitter, Map.of(
                    "code", ErrorCode.LLM_UNAVAILABLE.getCode(),
                    "msg", "AI 服务暂时不可用，请稍后再试"));
        }
    }

    /** 从请求构造模型配置：
     *  服务端模型（serverModel）用平台默认 Base URL/API Key + 选中模型；
     *  自定义模型（model+baseUrl+apiKey）用用户提供的配置；
     *  默认走 Spring AI 自动配置的模型。 */
    private LlmConfig toLlmConfig(ChatStreamRequest request) {
        // 服务端模型切换
        if (request.hasServerModel()) {
            String base = openAiCompatBaseUrl(springAiBaseUrl);
            return new LlmConfig(base, springAiApiKey, request.getServerModel());
        }
        // 自定义模型
        if (request.hasCustomModel()) {
            LlmConfig config = new LlmConfig(request.getBaseUrl(), request.getApiKey(), request.getModel());
            return config.isValid() ? config : null;
        }
        return null;
    }

    /** 将 Spring AI 的 Base URL（不带 /v1）转为 OpenAI 兼容地址（带 /v1） */
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
     * 意图识别节点，返回意图类型（结果缓存 30 分钟）
     */
    private IntentType recognizeIntent(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                       String history, LlmConfig config) {
        ThinkingNodeVO node = startThinking(thinking, "意图识别", "intent-agent", "正在分析用户意图");
        ssePublisher.send(emitter, "thinking", node);
        // 意图缓存按问题内容命中（历史敏感场景较少，短 TTL 兜底）
        String cacheKey = "intent:" + sha256(content);
        Optional<IntentType> cached = redisCacheService.get(cacheKey, IntentType.class);
        IntentType intent;
        if (cached.isPresent()) {
            intent = cached.get();
            finishThinking(thinking, emitter, "意图识别", "识别结果：" + intent.getLabel() + "（缓存命中）");
        } else {
            intent = intentAgent.identify(content, history, config);
            redisCacheService.put(cacheKey, intent, Duration.ofMinutes(30));
            finishThinking(thinking, emitter, "意图识别", "识别结果：" + intent.getLabel());
        }
        return intent;
    }

    /**
     * 查询意图路由 + 三路检索 + ReRead + 精排节点。
     *
     * <p>按 {@link RetrievalService.QueryType} 分流：
     * 术语定义 → 仅关键词+向量（最快）；对比类 → 查询分解+全链路；
     * 简单 → 仅向量；复杂 → 全链路（图谱+向量+关键词）。</p>
     */
    private RetrievalResult retrieve(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                     String history, LlmConfig config, AtomicBoolean cancelled) {
        long retrieveStart = System.currentTimeMillis();
        // 步骤 0：查询意图路由 —— 规则分类并选择检索路径
        ThinkingNodeVO routerNode = startThinking(thinking, "检索策略调度", "router-agent",
                "正在分析问题类型并选择检索路径");
        ssePublisher.send(emitter, "thinking", routerNode);
        RetrievalService.QueryType type = retrievalService.classifyQuery(content, config);
        String pathLabel = switch (type) {
            case DEFINITION -> "术语定义 → 关键词 + 向量检索（最快）";
            case COMPARE -> "对比问题 → 查询分解 + 聚合链路（图谱 + 向量 + 关键词）";
            case SIMPLE -> "简单问题 → 仅向量检索";
            case COMPLEX -> "复杂问题 → 聚合链路（图谱 + 向量 + 关键词）";
        };
        finishThinking(thinking, emitter, "检索策略调度", pathLabel);

        // 术语定义：仅关键词 + 向量，跳过图谱/HyDE/重写，响应最快
        if (type == RetrievalService.QueryType.DEFINITION) {
            EnhancedQuery simple = new EnhancedQuery(content, null, null);
            List<RetrievedChunk> vectorChunks = runVector(emitter, thinking, content, simple, cancelled);
            List<RetrievedChunk> keywordChunks = runKeyword(emitter, thinking, content, config, cancelled);
            List<RetrievedChunk> fused = retrievalService.fuse(vectorChunks, keywordChunks);
            ThinkingNodeVO fuseNode = startThinking(thinking, "融合与补全", "fusion-agent",
                    "正在融合关键词与向量结果");
            ssePublisher.send(emitter, "thinking", fuseNode);
            finishThinking(thinking, emitter, "融合与补全", "融合后共 " + fused.size() + " 条");
            emitRetrievalStats(emitter, 0, vectorChunks.size(), keywordChunks.size(), fused, retrieveStart);
            return new RetrievalResult(fused, true);
        }

        // 简单问题：仅向量检索原问题（跳过重写/HyDE 与图谱，响应更快）
        if (type == RetrievalService.QueryType.SIMPLE) {
            EnhancedQuery simple = new EnhancedQuery(content, null, null);
            List<RetrievedChunk> vectorChunks = runVector(emitter, thinking, content, simple, cancelled);
            emitRetrievalStats(emitter, 0, vectorChunks.size(), 0, vectorChunks, retrieveStart);
            return new RetrievalResult(vectorChunks, true);
        }

        // 对比/复杂问题：完整复合检索链路
        // 步骤 1：查询重写与 HyDE（并行；对比类还会做查询分解）
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
        finishThinking(thinking, emitter, "查询重写与 HyDE", enhanceDetail);

        // 步骤 2：图谱检索（local + global）
        ThinkingNodeVO graphNode = startThinking(thinking, "图谱检索", "graph-agent",
                "正在执行知识图谱检索");
        ssePublisher.send(emitter, "thinking", graphNode);
        List<RetrievedChunk> graphChunks = retrievalService.queryGraph(content, enhanced,
                progress -> pushProgress(emitter, graphNode, cancelled, progress));
        finishThinking(thinking, emitter, "图谱检索", "图谱命中 " + graphChunks.size() + " 条");

        // 步骤 3：向量检索（多查询扩展）
        List<RetrievedChunk> vectorChunks = runVector(emitter, thinking, content, enhanced, cancelled);

        // 步骤 4：关键词检索（术语/编号类问题召回更准）
        List<RetrievedChunk> keywordChunks = runKeyword(emitter, thinking, content, config, cancelled);

        // 步骤 5：三路融合 + ReRead 补全 + LLM 精排
        ThinkingNodeVO fuseNode = startThinking(thinking, "融合与补全", "fusion-agent",
                "正在融合三路结果并二次检索补全、精排");
        ssePublisher.send(emitter, "thinking", fuseNode);
        RetrievalResult result = retrievalService.fuseAndSupplement(content, graphChunks, vectorChunks,
                keywordChunks, enhanced, config);
        String fuseDetail = String.format("融合后共 %d 条%s", result.getChunks().size(),
                result.isDegraded() ? "（查询增强已降级）" : "");
        finishThinking(thinking, emitter, "融合与补全", fuseDetail);
        emitRetrievalStats(emitter, graphChunks.size(), vectorChunks.size(), keywordChunks.size(),
                result.getChunks(), retrieveStart);
        return result;
    }

    /** 推送「检索分析」数据（三路命中数 + 来源文档分布 + 耗时） */
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
        ssePublisher.send(emitter, "stats", stats);
    }

    /** 向量检索节点（含 SSE 进度推送） */
    private List<RetrievedChunk> runVector(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                           EnhancedQuery enhanced, AtomicBoolean cancelled) {
        ThinkingNodeVO vectorNode = startThinking(thinking, "向量检索", "vector-agent",
                "正在执行向量语义检索");
        ssePublisher.send(emitter, "thinking", vectorNode);
        List<RetrievedChunk> chunks = retrievalService.queryVector(content, enhanced,
                progress -> pushProgress(emitter, vectorNode, cancelled, progress));
        finishThinking(thinking, emitter, "向量检索", "向量命中 " + chunks.size() + " 条");
        return chunks;
    }

    /** 关键词检索节点（含 SSE 进度推送） */
    private List<RetrievedChunk> runKeyword(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                            LlmConfig config, AtomicBoolean cancelled) {
        ThinkingNodeVO kwNode = startThinking(thinking, "关键词检索", "keyword-agent",
                "正在执行关键词检索");
        ssePublisher.send(emitter, "thinking", kwNode);
        List<RetrievedChunk> chunks = retrievalService.queryKeyword(content, config,
                progress -> pushProgress(emitter, kwNode, cancelled, progress));
        finishThinking(thinking, emitter, "关键词检索", "关键词命中 " + chunks.size() + " 条");
        return chunks;
    }

    /** 推送检索过程进度（取消时停止推送） */
    private void pushProgress(SseEmitter emitter, ThinkingNodeVO node, AtomicBoolean cancelled, String progress) {
        if (cancelled != null && cancelled.get()) {
            return;
        }
        ssePublisher.send(emitter, "thinking",
                new ThinkingNodeVO(node.getStage(), node.getAgent(), "running", progress, null));
    }

    /** 截断长文本用于节点详情 */
    private String shortText(String text) {
        if (text == null) {
            return "无";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 30 ? oneLine.substring(0, 30) + "…" : oneLine;
    }

    /**
     * 推送引用来源事件（含章节路径与高亮术语）
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
        if (answer == null || answer.isBlank()) {
            // 最终降级：直接返回纯检索上下文
            answer = degradedAnswer(result);
            ssePublisher.send(emitter, "delta", Map.of("content", answer));
        }
        finishThinking(thinking, emitter, "总结生成", "回答生成完毕");
        return answer;
    }

    /**
     * 寒暄/系统咨询：直接应答，不进入检索链路
     */
    private String respondDirect(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                 LlmConfig config, AtomicBoolean cancelled) {
        ThinkingNodeVO node = startThinking(thinking, "直接应答", "answer-agent", "无需检索，直接应答");
        ssePublisher.send(emitter, "thinking", node);
        String answer = consume(emitter, llmService.callStream("chat", content, config), cancelled);
        if (answer == null || answer.isBlank()) {
            answer = "您好！我是「溯知」，可以为你解答《数据挖掘》课程相关问题，"
                    + "也可以询问平台的使用方式。请描述你的问题。";
            ssePublisher.send(emitter, "delta", Map.of("content", answer));
        }
        finishThinking(thinking, emitter, "直接应答", "应答完成");
        return answer;
    }

    /**
     * 流式生成回答文本
     */
    private String streamAnswer(SseEmitter emitter, String prompt, LlmConfig config, AtomicBoolean cancelled) {
        StringBuilder acc = new StringBuilder();
        acc.append(consume(emitter, answerAgent.streamAnswer(prompt, config), cancelled));
        if (acc.isEmpty()) {
            acc.append(consume(emitter, llmService.callStream("summary", prompt, config), cancelled));
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
     * 持久化 AI 消息并推送 done 事件。
     * 回答为空（用户中断且未产生任何内容）时不保存空消息，避免刷新后出现空记录。
     */
    private void persistAndFinish(ChatSession session, List<ThinkingNodeVO> thinking,
                                  List<ReferenceVO> references, String answer,
                                  long start, SseEmitter emitter) {
        long latency = System.currentTimeMillis() - start;
        if (answer == null || answer.isBlank()) {
            log.info("回答为空（可能被中断），不保存 AI 消息：session={}", session.getId());
            ssePublisher.send(emitter, "done", Map.of(
                    "sessionId", session.getId(),
                    "title", session.getTitle()));
            return;
        }
        ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer,
                thinking, references, latency);
        ssePublisher.send(emitter, "done", Map.of(
                "sessionId", session.getId(),
                "messageId", assistant.getId(),
                "title", session.getTitle()));
        log.info("问答完成：session={}, latency={}ms", session.getId(), latency);
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
        // 多轮历史上下文（帮助理解指代与延续话题）
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
     * 组装引用来源列表（含章节路径与高亮术语）
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

    /** 从用户问题提取用于片段内高亮的术语（规则切分，保留 ≥2 字符 token） */
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
     * 判断是否为无需检索的直接应答意图
     */
    private boolean isDirectAnswer(IntentType intent) {
        return intent == IntentType.GREETING || intent == IntentType.SYSTEM_QUESTION;
    }

    /**
     * 创建思考节点（加入追踪列表，不推送；推送由调用方统一执行）
     */
    private ThinkingNodeVO startThinking(List<ThinkingNodeVO> thinking, String stage,
                                         String agent, String message) {
        ThinkingNodeVO node = new ThinkingNodeVO(stage, agent, "running", message, null);
        thinking.add(node);
        return node;
    }

    /**
     * 完成思考节点：更新追踪列表中对应节点为 done 并推送给前端。
     * 保证持久化的思考链路状态正确（历史会话不会再显示"进行中"）。
     */
    private void finishThinking(List<ThinkingNodeVO> thinking, SseEmitter emitter, String stage, String detail) {
        for (int i = thinking.size() - 1; i >= 0; i--) {
            ThinkingNodeVO node = thinking.get(i);
            if (node.getStage().equals(stage) && "running".equals(node.getStatus())) {
                ThinkingNodeVO done = new ThinkingNodeVO(node.getStage(), node.getAgent(),
                        "done", node.getMessage(), detail);
                thinking.set(i, done);
                ssePublisher.send(emitter, "thinking", done);
                return;
            }
        }
    }

    /**
     * 异常时将未完成节点标记为失败
     */
    private void markThinkingFailed(List<ThinkingNodeVO> thinking) {
        for (int i = thinking.size() - 1; i >= 0; i--) {
            ThinkingNodeVO node = thinking.get(i);
            if ("running".equals(node.getStatus())) {
                thinking.set(i, new ThinkingNodeVO(node.getStage(), node.getAgent(), "failed",
                        node.getMessage(), "执行失败，已降级"));
                break;
            }
        }
    }

    /** SHA-256 摘要（缓存 key 用，Hutool） */
    private String sha256(String text) {
        return SecureUtil.sha256(text);
    }
}