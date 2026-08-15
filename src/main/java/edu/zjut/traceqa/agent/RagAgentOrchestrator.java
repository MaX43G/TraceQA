package edu.zjut.traceqa.agent;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.enums.IntentType;
import edu.zjut.traceqa.config.LlmConfig;
import edu.zjut.traceqa.dto.chat.ChatStreamRequest;
import edu.zjut.traceqa.dto.chat.ReferenceVO;
import edu.zjut.traceqa.dto.chat.ThinkingNodeVO;
import edu.zjut.traceqa.entity.ChatMessage;
import edu.zjut.traceqa.entity.ChatSession;
import edu.zjut.traceqa.retrieval.EnhancedQuery;
import edu.zjut.traceqa.retrieval.RetrievedChunk;
import edu.zjut.traceqa.retrieval.RetrievalResult;
import edu.zjut.traceqa.retrieval.RetrievalService;
import edu.zjut.traceqa.service.ChatService;
import edu.zjut.traceqa.service.LlmService;
import edu.zjut.traceqa.sse.SsePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final ChatService chatService;
    private final IntentAgent intentAgent;
    private final AnswerAgent answerAgent;
    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final SsePublisher ssePublisher;

    public RagAgentOrchestrator(ChatService chatService, IntentAgent intentAgent, AnswerAgent answerAgent,
                                RetrievalService retrievalService, LlmService llmService, SsePublisher ssePublisher) {
        this.chatService = chatService;
        this.intentAgent = intentAgent;
        this.answerAgent = answerAgent;
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.ssePublisher = ssePublisher;
    }

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
            // 1. 获取/创建会话并保存用户消息
            ChatSession session = chatService.getOrCreateSession(userId, request.sessionId(),
                    request.knowledgeBaseId(), request.content());
            chatService.saveUserMessage(session.getId(), request.content());

            // 2. 意图识别
            IntentType intent = recognizeIntent(emitter, thinking, request.content(), modelConfig);

            // 3. 课程问答走 RAG 检索链路，否则直接应答
            String answer;
            List<ReferenceVO> references = List.of();
            if (isDirectAnswer(intent)) {
                answer = respondDirect(emitter, thinking, request.content(), modelConfig, cancelled);
            } else {
                RetrievalResult result = retrieve(emitter, thinking, request.content(), modelConfig, cancelled);
                references = emitReferences(emitter, result);
                answer = generateAnswer(emitter, thinking, request.content(), result, modelConfig, cancelled);
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

    /** 从请求构造自定义模型配置（未携带则返回 null 使用默认模型） */
    private LlmConfig toLlmConfig(ChatStreamRequest request) {
        if (!request.hasCustomModel()) {
            return null;
        }
        LlmConfig config = new LlmConfig(request.baseUrl(), request.apiKey(), request.model());
        return config.isValid() ? config : null;
    }

    /**
     * 意图识别节点，返回意图类型
     */
    private IntentType recognizeIntent(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                       LlmConfig config) {
        ThinkingNodeVO node = startThinking(thinking, "意图识别", "intent-agent", "正在分析用户意图");
        ssePublisher.send(emitter, "thinking", node);
        IntentType intent = intentAgent.identify(content, config);
        finishThinking(thinking, emitter, "意图识别", "识别结果：" + intent.getLabel());
        return intent;
    }

    /**
     * 查询增强 + 双路检索 + ReRead 节点
     */
    private RetrievalResult retrieve(SseEmitter emitter, List<ThinkingNodeVO> thinking, String content,
                                     LlmConfig config, AtomicBoolean cancelled) {
        // 步骤 1：查询重写与 HyDE（并行）
        ThinkingNodeVO enhanceNode = startThinking(thinking, "查询重写与 HyDE", "rewrite-agent",
                "正在生成查询重写与假设性文档");
        ssePublisher.send(emitter, "thinking", enhanceNode);
        EnhancedQuery enhanced = retrievalService.enhance(content, config,
                progress -> pushProgress(emitter, enhanceNode, cancelled, progress));
        String enhanceDetail = String.format("重写：%s", shortText(enhanced.rewritten()));
        finishThinking(thinking, emitter, "查询重写与 HyDE", enhanceDetail);

        // 步骤 2：图谱检索（local + global）
        ThinkingNodeVO graphNode = startThinking(thinking, "图谱检索", "graph-agent",
                "正在执行知识图谱检索");
        ssePublisher.send(emitter, "thinking", graphNode);
        List<RetrievedChunk> graphChunks = retrievalService.queryGraph(content, enhanced, config,
                progress -> pushProgress(emitter, graphNode, cancelled, progress));
        finishThinking(thinking, emitter, "图谱检索", "图谱命中 " + graphChunks.size() + " 条");

        // 步骤 3：向量检索（多查询扩展）
        ThinkingNodeVO vectorNode = startThinking(thinking, "向量检索", "vector-agent",
                "正在执行向量语义检索");
        ssePublisher.send(emitter, "thinking", vectorNode);
        List<RetrievedChunk> vectorChunks = retrievalService.queryVector(content, enhanced, config,
                progress -> pushProgress(emitter, vectorNode, cancelled, progress));
        finishThinking(thinking, emitter, "向量检索", "向量命中 " + vectorChunks.size() + " 条");

        // 步骤 4：融合与 ReRead 补全
        ThinkingNodeVO fuseNode = startThinking(thinking, "融合与补全", "fusion-agent",
                "正在融合双路结果并二次检索补全");
        ssePublisher.send(emitter, "thinking", fuseNode);
        RetrievalResult result = retrievalService.fuseAndSupplement(content, graphChunks, vectorChunks,
                enhanced, config);
        String fuseDetail = String.format("融合后共 %d 条%s", result.chunks().size(),
                result.degraded() ? "（查询增强已降级）" : "");
        finishThinking(thinking, emitter, "融合与补全", fuseDetail);
        return result;
    }

    /** 推送检索过程进度（取消时停止推送） */
    private void pushProgress(SseEmitter emitter, ThinkingNodeVO node, AtomicBoolean cancelled, String progress) {
        if (cancelled != null && cancelled.get()) {
            return;
        }
        ssePublisher.send(emitter, "thinking",
                new ThinkingNodeVO(node.stage(), node.agent(), "running", progress, null));
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
     * 推送引用来源事件
     */
    private List<ReferenceVO> emitReferences(SseEmitter emitter, RetrievalResult result) {
        List<ReferenceVO> references = buildReferences(result);
        ssePublisher.send(emitter, "references", Map.of("references", references));
        return references;
    }

    /**
     * 流式生成回答：Alibaba Agent 优先，ChatClient 兜底
     */
    private String generateAnswer(SseEmitter emitter, List<ThinkingNodeVO> thinking,
                                  String content, RetrievalResult result, LlmConfig config,
                                  AtomicBoolean cancelled) {
        ThinkingNodeVO node = startThinking(thinking, "总结生成", "answer-agent", "正在生成回答");
        ssePublisher.send(emitter, "thinking", node);

        String prompt = buildAnswerPrompt(content, result);
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
        StringBuilder acc = new StringBuilder();
        acc.append(consume(emitter, llmService.callStream("chat", content, config), cancelled));
        String answer = acc.toString();
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
        flux.takeWhile(chunk -> !cancelled.get()).toIterable().forEach(chunk -> {
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
        for (RetrievedChunk chunk : result.chunks()) {
            sb.append("**片段 ").append(idx).append("** [citation:").append(idx).append("]\n\n")
                    .append(chunk.content()).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    /**
     * 组装「问题 + 上下文」总结提示词
     */
    private String buildAnswerPrompt(String question, RetrievalResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户问题】\n").append(question).append("\n\n【检索上下文】\n");
        if (result == null || !result.hasContent()) {
            sb.append("（未检索到相关上下文，请如实告知用户资料库中暂无相关内容）");
            return sb.toString();
        }
        int idx = 1;
        for (RetrievedChunk chunk : result.chunks()) {
            sb.append("[citation:").append(idx).append("] ")
                    .append(chunk.content()).append("\n\n");
            idx++;
        }
        return sb.toString();
    }

    /**
     * 组装引用来源列表
     */
    private List<ReferenceVO> buildReferences(RetrievalResult result) {
        if (result == null || !result.hasContent()) {
            return List.of();
        }
        List<ReferenceVO> refs = new ArrayList<>();
        int idx = 1;
        for (RetrievedChunk chunk : result.chunks()) {
            refs.add(new ReferenceVO(idx, extractFilename(chunk.filePath()), chunk.filePath(), chunk.content()));
            idx++;
        }
        return refs;
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
            if (node.stage().equals(stage) && "running".equals(node.status())) {
                ThinkingNodeVO done = new ThinkingNodeVO(node.stage(), node.agent(),
                        "done", node.message(), detail);
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
            if ("running".equals(node.status())) {
                thinking.set(i, new ThinkingNodeVO(node.stage(), node.agent(), "failed",
                        node.message(), "执行失败，已降级"));
                break;
            }
        }
    }
}