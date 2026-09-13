package edu.zjut.traceqa.qaservice.agent;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.model.dto.ChatStreamRequest;
import edu.zjut.traceqa.common.model.dto.LlmConfig;
import edu.zjut.traceqa.common.model.dto.RetrievalResult;
import edu.zjut.traceqa.common.model.dto.RetrievedChunk;
import edu.zjut.traceqa.common.model.po.ChatMessage;
import edu.zjut.traceqa.common.model.po.ChatSession;
import edu.zjut.traceqa.common.model.vo.ReferenceVO;
import edu.zjut.traceqa.common.model.vo.ThinkingNodeVO;
import edu.zjut.traceqa.qaservice.agent.react.GraphSearchTool;
import edu.zjut.traceqa.qaservice.agent.react.KeywordSearchTool;
import edu.zjut.traceqa.qaservice.agent.react.ReActTool;
import edu.zjut.traceqa.qaservice.agent.react.VectorSearchTool;
import edu.zjut.traceqa.qaservice.retrieval.RetrievalService;
import edu.zjut.traceqa.qaservice.service.ChatService;
import edu.zjut.traceqa.qaservice.service.LlmService;
import edu.zjut.traceqa.qaservice.service.OpenAiCompatClient;
import edu.zjut.traceqa.qaservice.sse.SsePublisher;
import edu.zjut.traceqa.qaservice.metrics.RagMetrics;
import edu.zjut.traceqa.qaservice.observability.LangfuseTraceService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI Decision 模式处理器（ReAct 自主决策）。
 *
 * <p>基于 ReAct（Reasoning + Acting）模式，LLM 通过工具调用自主决策检索策略：
 * <ul>
 *   <li>LLM 自主决定是否需要检索</li>
 *   <li>LLM 自主选择使用哪个检索工具（vector_search / graph_search / keyword_search）</li>
 *   <li>LLM 自主决定调用多少次（最多 {@code maxRounds} 轮）</li>
 *   <li>每轮工具调用结果作为 observation 反馈给 LLM</li>
 * </ul>
 *
 * <p>工作流：用户问题 → [思考 → 工具调用 → 观察] × N 轮 → 最终回答</p>
 */
@Component
public class AiDecisionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiDecisionHandler.class);

    private static final int MAX_CONTEXT_CHUNKS = 8;
    private static final int MAX_HISTORY_ROUNDS = 6;
    private static final int DEFAULT_MAX_ROUNDS = 5;

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
    @Resource
    private VectorSearchTool vectorSearchTool;
    @Resource
    private GraphSearchTool graphSearchTool;
    @Resource
    private KeywordSearchTool keywordSearchTool;
    @Resource
    private LangfuseTraceService langfuseTrace;

    @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}")
    private String springAiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String springAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * AI Decision 模式 SSE 流式执行入口
     */
    public void streamChat(Long userId, ChatStreamRequest request, SseEmitter emitter, AtomicBoolean cancelled) {
        ragMetrics.queryStart();
        List<ThinkingNodeVO> thinking = new ArrayList<>();
        StringBuilder reasoningAccumulator = new StringBuilder();
        long start = System.currentTimeMillis();
        LlmConfig modelConfig = toLlmConfig(request);

        // Langfuse trace
        Map<String, Object> traceMeta = new LinkedHashMap<>();
        traceMeta.put("userId", userId);
        traceMeta.put("mode", "ai-decision");
        traceMeta.put("content", request.getContent());
        String traceId = langfuseTrace.createTrace("rag-ai-decision-query", traceMeta);

        try {
            ChatSession session = chatService.getOrCreateSession(userId, request.getSessionId(),
                    request.getKnowledgeBaseId(), request.getContent());
            String history = chatService.buildHistoryText(session.getId(), MAX_HISTORY_ROUNDS);
            chatService.saveUserMessage(session.getId(), request.getContent());

            // Langfuse: ReAct 决策循环 span
            String reactSpanId = langfuseTrace.startSpan(traceId, "ReAct决策循环", request.getContent());

            // ReAct 决策循环
            ReActResult reactResult = reactLoop(emitter, thinking, request.getContent(), history,
                    modelConfig, cancelled);

            langfuseTrace.endSpan(reactSpanId, null, Map.of(
                    "rounds", reactResult.result().getChunks().size(),
                    "decidedToRetrieve", reactResult.decidedToRetrieve()));

            // 生成最终回答
            List<String> highlight = extractHighlightTerms(request.getContent());
            List<ReferenceVO> references = emitReferences(emitter, reactResult.result(), highlight);
            String answer = generateAnswer(emitter, thinking, request.getContent(), history,
                    reactResult.result(), modelConfig, cancelled, reasoningAccumulator);

            langfuseTrace.updateTrace(traceId, Map.of(
                    "latencyMs", System.currentTimeMillis() - start,
                    "answerLength", answer.length(),
                    "decidedToRetrieve", reactResult.decidedToRetrieve()));
            langfuseTrace.flush();

            persistAndFinish(session, thinking, references, answer, reasoningAccumulator.toString(), start, emitter, traceId);
            ssePublisher.complete(emitter);
        } catch (Exception e) {
            langfuseTrace.recordEvent(traceId, "error", Map.of("error", e.getMessage()));
            langfuseTrace.flush();
            String trace = java.util.Arrays.stream(e.getStackTrace())
                    .limit(5)
                    .map(StackTraceElement::toString)
                    .collect(java.util.stream.Collectors.joining("\n  "));
            log.error("AI Decision 编排异常 [{}]: {}\n  {}", e.getClass().getSimpleName(), e.getMessage(), trace);
            markThinkingFailed(thinking);
            ssePublisher.completeWithError(emitter, Map.of(
                    "code", ErrorCode.LLM_UNAVAILABLE.getCode(),
                    "msg", "AI 服务暂时不可用，请稍后再试"));
        } finally {
            ragMetrics.queryEnd();
        }
    }

    /**
     * ReAct 核心循环：思考 → 工具调用 → 观察 × N 轮
     */
    private ReActResult reactLoop(SseEmitter emitter, List<ThinkingNodeVO> thinking,
                                   String question, String history, LlmConfig config,
                                   AtomicBoolean cancelled) {
        List<RetrievedChunk> allChunks = new ArrayList<>();
        List<String> toolCallLog = new ArrayList<>();

        // 工具注册表
        Map<String, ReActTool> tools = new LinkedHashMap<>();
        tools.put("vector_search", vectorSearchTool);
        tools.put("graph_search", graphSearchTool);
        tools.put("keyword_search", keywordSearchTool);

        // 构建 ReAct 提示词
        String toolDescriptions = buildToolDescriptions(tools);

        ThinkingNodeVO reactNode = startThinking(thinking, "AI 决策循环", "react-agent",
                "LLM 正在自主决策检索策略");
        ssePublisher.send(emitter, "thinking", reactNode);

        StringBuilder conversationHistory = new StringBuilder();
        conversationHistory.append("用户问题：").append(question).append("\n");
        if (history != null && !history.isBlank()) {
            conversationHistory.append("对话历史：\n").append(history).append("\n");
        }

        int round;
        boolean decidedToRetrieve = false;

        for (round = 1; round <= DEFAULT_MAX_ROUNDS; round++) {
            if (cancelled != null && cancelled.get()) {
                break;
            }

            String roundPrompt = buildReactPrompt(question, conversationHistory.toString(),
                    toolDescriptions, round, toolCallLog);
            pushProgress(emitter, reactNode, cancelled, "第 " + round + " 轮决策中...");

            String llmOutput = llmService.call("react_decision", roundPrompt, config);
            if (llmOutput == null || llmOutput.isBlank()) {
                log.warn("ReAct 第 {} 轮 LLM 输出为空", round);
                break;
            }

            // 解析 LLM 输出
            ReactDecision decision = parseDecision(llmOutput);

            if (decision.type == DecisionType.NO_RETRIEVE) {
                // LLM 决定不检索，直接回答
                log.info("ReAct 第 {} 轮：LLM 决定不检索", round);
                toolCallLog.add("第 " + round + " 轮：决定不检索，直接回答");
                reactNode.setData(Map.of("rounds", round, "decision", "no_retrieve",
                        "toolCallLog", toolCallLog));
                break;
            }

            if (decision.type == DecisionType.FINAL_ANSWER) {
                // LLM 给出最终答案（包含在决策中）
                log.info("ReAct 第 {} 轮：LLM 给出最终答案", round);
                toolCallLog.add("第 " + round + " 轮：给出最终答案");
                reactNode.setData(Map.of("rounds", round, "decision", "final_answer",
                        "toolCallLog", toolCallLog));
                break;
            }

            if (decision.type == DecisionType.TOOL_CALL) {
                // 执行工具调用
                decidedToRetrieve = true;
                ReActTool tool = tools.get(decision.toolName);
                if (tool == null) {
                    toolCallLog.add("第 " + round + " 轮：未知工具 " + decision.toolName + "，跳过");
                    conversationHistory.append("\n第 ").append(round).append(" 轮：\n")
                            .append("思考：").append(decision.thought).append("\n")
                            .append("工具调用：").append(decision.toolName).append("\n")
                            .append("结果：错误 - 未知工具 ").append(decision.toolName).append("\n");
                    continue;
                }

                ThinkingNodeVO toolNode = startThinking(thinking, "工具调用 · " + decision.toolName,
                        "react-tool", "正在调用 " + decision.toolName + ": " + shortText(decision.toolInput));
                ssePublisher.send(emitter, "thinking", toolNode);

                String observation;
                try {
                    observation = tool.execute(decision.toolInput);
                } catch (Exception e) {
                    observation = "[工具调用失败] " + e.getMessage();
                    log.warn("ReAct 工具调用异常：tool={}, err={}", decision.toolName, e.getMessage());
                }

                toolNode.setData(Map.of(
                        "tool", decision.toolName,
                        "input", decision.toolInput,
                        "observationLength", observation.length()));
                finishThinking(thinking, emitter, "工具调用 · " + decision.toolName,
                        decision.toolName + " 返回 " + observation.length() + " 字");

                toolCallLog.add("第 " + round + " 轮：调用 " + decision.toolName
                        + "(" + shortText(decision.toolInput) + ")");

                // 将工具结果加入对话历史
                conversationHistory.append("\n第 ").append(round).append(" 轮：\n")
                        .append("思考：").append(decision.thought).append("\n")
                        .append("工具调用：").append(decision.toolName).append("(").append(decision.toolInput).append(")\n")
                        .append("观察结果：\n").append(observation).append("\n");

                // 收集检索到的片段
                List<RetrievedChunk> toolChunks = parseToolChunks(observation, decision.toolName);
                allChunks.addAll(toolChunks);
            }
        }

        reactNode.setData(Map.of(
                "rounds", round - 1,
                "decidedToRetrieve", decidedToRetrieve,
                "totalChunks", allChunks.size(),
                "toolCallLog", toolCallLog));
        finishThinking(thinking, emitter, "AI 决策循环",
                "完成 " + (round - 1) + " 轮决策，收集 " + allChunks.size() + " 条片段");

        // 融合所有检索结果
        List<RetrievedChunk> fused = retrievalService.fuse(List.of(allChunks));
        emitRetrievalStats(emitter, allChunks, fused);

        return new ReActResult(new RetrievalResult(fused, false), decidedToRetrieve);
    }

    /**
     * 构建工具描述文本
     */
    private String buildToolDescriptions(Map<String, ReActTool> tools) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ReActTool> entry : tools.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().description()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建 ReAct 提示词
     */
    private String buildReactPrompt(String question, String conversationHistory,
                                     String toolDescriptions, int round, List<String> toolCallLog) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能检索决策 Agent。根据用户问题，你需要决定是否需要检索知识库来获取信息。\n\n");

        sb.append("## 可用工具\n");
        sb.append(toolDescriptions).append("\n");

        sb.append("## 用户问题\n");
        sb.append(question).append("\n");

        if (!toolCallLog.isEmpty()) {
            sb.append("\n## 已执行的操作\n");
            for (String logEntry : toolCallLog) {
                sb.append("- ").append(logEntry).append("\n");
            }
        }

        sb.append("\n## 当前轮次：第 ").append(round).append(" 轮（最多 5 轮）\n\n");

        sb.append("## 输出格式\n");
        sb.append("请根据以上信息，输出以下三种格式之一：\n\n");

        sb.append("### 1. 需要调用工具时，输出 JSON：\n");
        sb.append("```json\n");
        sb.append("{\"thought\": \"你的思考过程\", \"action\": \"工具名称\", \"action_input\": \"查询内容\"}\n");
        sb.append("```\n\n");

        sb.append("### 2. 信息已足够，给出最终回答时，输出 JSON：\n");
        sb.append("```json\n");
        sb.append("{\"thought\": \"已有足够信息\", \"final_answer\": \"你的完整回答\"}\n");
        sb.append("```\n\n");

        sb.append("### 3. 认为不需要检索时（如寒暄、系统咨询），输出 JSON：\n");
        sb.append("```json\n");
        sb.append("{\"thought\": \"不需要检索\", \"no_retrieve\": true}\n");
        sb.append("```\n\n");

        sb.append("注意：\n");
        sb.append("- 请合理规划检索策略，避免重复调用同一工具\n");
        sb.append("- 每次工具调用的查询内容应有针对性，不要直接复制用户原始问题\n");
        sb.append("- 如果已有足够的检索结果，应尽快给出最终回答\n");

        return sb.toString();
    }

    /**
     * 解析 LLM 的 ReAct 决策输出
     */
    private ReactDecision parseDecision(String llmOutput) {
        ReactDecision decision = new ReactDecision();

        // 尝试从输出中提取 JSON
        String json = extractJson(llmOutput);
        if (json == null) {
            // 无法解析，默认不检索
            decision.type = DecisionType.NO_RETRIEVE;
            decision.thought = "无法解析 LLM 输出";
            return decision;
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            if (node.has("no_retrieve") && node.get("no_retrieve").asBoolean()) {
                decision.type = DecisionType.NO_RETRIEVE;
                decision.thought = node.has("thought") ? node.get("thought").asText() : "";
            } else if (node.has("final_answer")) {
                decision.type = DecisionType.FINAL_ANSWER;
                decision.thought = node.has("thought") ? node.get("thought").asText() : "";
                decision.finalAnswer = node.get("final_answer").asText();
            } else if (node.has("action")) {
                decision.type = DecisionType.TOOL_CALL;
                decision.thought = node.has("thought") ? node.get("thought").asText() : "";
                decision.toolName = node.get("action").asText();
                decision.toolInput = node.has("action_input") ? node.get("action_input").asText() : "";
            } else {
                decision.type = DecisionType.NO_RETRIEVE;
                decision.thought = "无法识别的 JSON 格式";
            }
        } catch (Exception e) {
            log.warn("ReAct JSON 解析失败：{}", e.getMessage());
            // 回退：尝试关键词匹配
            decision = parseDecisionFallback(llmOutput);
        }

        return decision;
    }

    /**
     * ReAct 决策解析回退：关键词匹配
     */
    private ReactDecision parseDecisionFallback(String llmOutput) {
        ReactDecision decision = new ReactDecision();
        String lower = llmOutput.toLowerCase();

        if (lower.contains("no_retrieve") || lower.contains("不需要检索")) {
            decision.type = DecisionType.NO_RETRIEVE;
            decision.thought = llmOutput;
        } else if (lower.contains("final_answer") || lower.contains("最终回答")) {
            decision.type = DecisionType.FINAL_ANSWER;
            decision.thought = llmOutput;
            decision.finalAnswer = llmOutput;
        } else if (lower.contains("vector_search")) {
            decision.type = DecisionType.TOOL_CALL;
            decision.toolName = "vector_search";
            decision.toolInput = extractActionInput(llmOutput);
            decision.thought = llmOutput;
        } else if (lower.contains("graph_search")) {
            decision.type = DecisionType.TOOL_CALL;
            decision.toolName = "graph_search";
            decision.toolInput = extractActionInput(llmOutput);
            decision.thought = llmOutput;
        } else if (lower.contains("keyword_search")) {
            decision.type = DecisionType.TOOL_CALL;
            decision.toolName = "keyword_search";
            decision.toolInput = extractActionInput(llmOutput);
            decision.thought = llmOutput;
        } else {
            decision.type = DecisionType.NO_RETRIEVE;
            decision.thought = "无法解析";
        }

        return decision;
    }

    /**
     * 从文本中提取 JSON 块
     */
    private String extractJson(String text) {
        // 尝试提取 ```json ... ``` 块
        int jsonStart = text.indexOf("```json");
        if (jsonStart >= 0) {
            int jsonEnd = text.indexOf("```", jsonStart + 7);
            if (jsonEnd > jsonStart) {
                return text.substring(jsonStart + 7, jsonEnd).trim();
            }
        }
        // 尝试提取裸 JSON 对象
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }
        return null;
    }

    /**
     * 从文本中提取 action_input 值
     */
    private String extractActionInput(String text) {
        int start = text.indexOf("\"action_input\"");
        if (start < 0) {
            start = text.indexOf("action_input");
        }
        if (start < 0) {
            return text;
        }
        int colonIdx = text.indexOf(':', start);
        if (colonIdx < 0) {
            return text.substring(start).trim();
        }
        int quoteStart = text.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) {
            return text.substring(colonIdx + 1).trim();
        }
        int quoteEnd = text.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return text.substring(quoteStart + 1).trim();
        }
        return text.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 从工具观察文本中解析检索片段
     */
    private List<RetrievedChunk> parseToolChunks(String observation, String toolName) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        if (observation == null || observation.isBlank()) {
            return chunks;
        }

        String[] sections = observation.split("--- 结果 \\d+ ---");
        for (String section : sections) {
            if (section.trim().isBlank()) {
                continue;
            }
            String source = toolName.replace("_search", "");
            String content = section.trim();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            chunks.add(new RetrievedChunk(null, "", content, 0, source, List.of()));
        }

        return chunks;
    }

    // ========== 以下为辅助方法 ==========

    private LlmConfig toLlmConfig(ChatStreamRequest request) {
        if (request.hasServerModel()) {
            String base = openAiCompatBaseUrl(springAiBaseUrl);
            return new LlmConfig(base, springAiApiKey, request.getServerModel());
        }
        if (request.hasCustomModel()) {
            LlmConfig config = new LlmConfig(request.getBaseUrl(), request.getApiKey(), request.getModel());
            return config.isValid() ? config : null;
        }
        String base = openAiCompatBaseUrl(springAiBaseUrl);
        return new LlmConfig(base, springAiApiKey, null);
    }

    private String openAiCompatBaseUrl(String baseUrl) {
        if (baseUrl == null) return null;
        String base = baseUrl.trim();
        if (base.isBlank()) return "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.contains("/v1")) return base;
        return base + "/v1";
    }

    private String generateAnswer(SseEmitter emitter, List<ThinkingNodeVO> thinking,
                                  String content, String history, RetrievalResult result,
                                  LlmConfig config, AtomicBoolean cancelled,
                                  StringBuilder reasoningAccumulator) {
        ThinkingNodeVO node = startThinking(thinking, "总结生成", "answer-agent", "正在生成回答");
        ssePublisher.send(emitter, "thinking", node);

        String prompt = buildAnswerPrompt(content, history, result);

        String answer = consumeWithReasoning(emitter,
                llmService.callStreamWithReasoning("summary", prompt, config), cancelled, reasoningAccumulator);
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
        return answer;
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

    private void emitRetrievalStats(SseEmitter emitter, List<RetrievedChunk> allChunks,
                                    List<RetrievedChunk> fused) {
        int vectorHits = 0, graphHits = 0, keywordHits = 0;
        for (RetrievedChunk c : allChunks) {
            switch (c.getSource()) {
                case "vector" -> vectorHits++;
                case "graph" -> graphHits++;
                case "keyword" -> keywordHits++;
            }
        }
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
        stats.put("sourceDocs", sourceDocs);
        ragMetrics.recordRetrievalHits("graph", graphHits);
        ragMetrics.recordRetrievalHits("vector", vectorHits);
        ragMetrics.recordRetrievalHits("keyword", keywordHits);
        ssePublisher.send(emitter, "stats", stats);
    }

    private void persistAndFinish(ChatSession session, List<ThinkingNodeVO> thinking,
                                  List<ReferenceVO> references, String answer,
                                  String reasoningContent, long start, SseEmitter emitter,
                                  String traceId) {
        long latency = System.currentTimeMillis() - start;
        String traceUrl = langfuseTrace.getTraceUrl(traceId);
        if (answer == null || answer.isBlank()) {
            ragMetrics.recordQueryLatency(latency, "unknown");
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", session.getId());
            done.put("title", session.getTitle());
            if (traceUrl != null) done.put("traceUrl", traceUrl);
            ssePublisher.send(emitter, "done", done);
            return;
        }
        try {
            ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer,
                    thinking, references, reasoningContent, latency);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", session.getId());
            done.put("messageId", assistant.getId());
            done.put("title", session.getTitle());
            if (traceUrl != null) done.put("traceUrl", traceUrl);
            ssePublisher.send(emitter, "done", done);
            ragMetrics.recordQueryLatency(latency, "success");
            log.info("AI Decision 问答完成：session={}, latency={}ms", session.getId(), latency);
        } catch (Exception e) {
            log.warn("持久化消息失败：{}", e.getMessage());
            try {
                ChatMessage assistant = chatService.saveAssistantMessage(session.getId(), answer,
                        List.of(), references, reasoningContent, latency);
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("sessionId", session.getId());
                done.put("messageId", assistant.getId());
                done.put("title", session.getTitle());
                if (traceUrl != null) done.put("traceUrl", traceUrl);
                ssePublisher.send(emitter, "done", done);
            } catch (Exception ex) {
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("sessionId", session.getId());
                done.put("title", session.getTitle());
                if (traceUrl != null) done.put("traceUrl", traceUrl);
                ssePublisher.send(emitter, "done", done);
            }
        }
    }

    private String consumeWithReasoning(SseEmitter emitter, Flux<OpenAiCompatClient.DeltaChunk> flux,
                                        AtomicBoolean cancelled, StringBuilder reasoningAccumulator) {
        StringBuilder acc = new StringBuilder();
        flux.takeWhile(_ -> !cancelled.get()).toIterable().forEach(chunk -> {
            if (!chunk.reasoningContent().isEmpty()) {
                if (reasoningAccumulator != null) {
                    reasoningAccumulator.append(chunk.reasoningContent());
                }
                ssePublisher.send(emitter, "reasoning", Map.of("content", chunk.reasoningContent()));
            }
            if (!chunk.content().isEmpty()) {
                acc.append(chunk.content());
                ssePublisher.send(emitter, "delta", Map.of("content", chunk.content()));
            }
        });
        return acc.toString();
    }

    private void pushProgress(SseEmitter emitter, ThinkingNodeVO node, AtomicBoolean cancelled, String progress) {
        if (cancelled != null && cancelled.get()) return;
        ssePublisher.send(emitter, "thinking",
                new ThinkingNodeVO(node.getStage(), node.getAgent(), "running", progress, null));
    }

    private List<String> extractHighlightTerms(String question) {
        if (question == null || question.isBlank()) return List.of();
        return java.util.Arrays.stream(question.split("[\\s,，。？?！!、；;：:（()）\\[\\]【】\"']+"))
                .filter(w -> w.length() >= 2)
                .limit(6)
                .toList();
    }

    private String extractFilename(String path) {
        if (path == null || path.isBlank()) return "";
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private String shortText(String text) {
        if (text == null || text.isBlank()) return "（空）";
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 30 ? oneLine.substring(0, 30) + "…" : oneLine;
    }

    private ThinkingNodeVO startThinking(List<ThinkingNodeVO> thinking, String stage,
                                         String agent, String message) {
        ThinkingNodeVO node = new ThinkingNodeVO(stage, agent, "running", message, null);
        node.setStartMillis(System.currentTimeMillis());
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
                    node.setDetail(summary);
                    node.setCostMs(System.currentTimeMillis() - node.getStartMillis());
                    ssePublisher.send(emitter, "thinking", node);
                    return;
                }
            }
        }
    }

    private void markThinkingFailed(List<ThinkingNodeVO> thinking) {
        synchronized (thinking) {
            for (int i = thinking.size() - 1; i >= 0; i--) {
                ThinkingNodeVO node = thinking.get(i);
                if ("running".equals(node.getStatus())) {
                    node.setStatus("failed");
                    node.setDetail("执行失败");
                    node.setCostMs(System.currentTimeMillis() - node.getStartMillis());
                    break;
                }
            }
        }
    }

    // ========== 内部数据类 ==========

    private enum DecisionType {
        TOOL_CALL,
        FINAL_ANSWER,
        NO_RETRIEVE
    }

    private static class ReactDecision {
        DecisionType type;
        String thought;
        String toolName;
        String toolInput;
        String finalAnswer;
    }

    private record ReActResult(RetrievalResult result, boolean decidedToRetrieve) {}
}
