package edu.zjut.traceqa.agent;

import jakarta.annotation.Resource;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import edu.zjut.traceqa.common.enums.IntentType;
import edu.zjut.traceqa.model.dto.LlmConfig;
import edu.zjut.traceqa.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 意图识别 Agent。
 *
 * <p>基于 Spring AI Alibaba 的 ReAct 智能体识别用户意图；
 * 使用自定义模型时改走 {@link LlmService}（OpenAI 兼容直连）。
 * 失败时优雅降级返回 {@link IntentType#UNKNOWN}（按课程问答兜底）。</p>
 */
@Slf4j
@Component
public class IntentAgent {

    @Resource
    private RagAgents ragAgents;

    @Resource
    private LlmService llmService;

    

    /**
     * 识别用户消息意图（支持多轮历史）。
     *
     * @param message 用户消息
     * @param history 对话历史文本（可为空）
     * @param config  自定义模型配置（null 表示使用默认模型）
     * @return 意图类型（失败返回 UNKNOWN）
     */
    public IntentType identify(String message, String history, LlmConfig config) {
        String input = buildContextInput(message, history);
        // 自定义模型：直接走 OpenAI 兼容直连
        if (config != null && config.isValid()) {
            String text = llmService.call("intent", input, config);
            return parseIntent(text);
        }
        ReactAgent agent = ragAgents.intentAgent();
        if (agent == null) {
            log.debug("意图 Agent 未就绪，降级为 UNKNOWN");
            return IntentType.UNKNOWN;
        }
        try {
            String text = agent.call(input).getText();
            return parseIntent(text);
        } catch (Exception e) {
            log.warn("意图识别失败，降级为 UNKNOWN：{}", e.getMessage());
            return IntentType.UNKNOWN;
        }
    }

    /** 拼接对话历史与当前消息（无历史时仅当前消息） */
    private String buildContextInput(String message, String history) {
        if (history == null || history.isBlank()) {
            return message;
        }
        return "对话历史：\n" + history + "当前用户消息：" + message;
    }

    /** 解析模型输出的意图编码 */
    private IntentType parseIntent(String raw) {
        if (raw == null || raw.isBlank()) {
            return IntentType.UNKNOWN;
        }
        String upper = raw.trim().toUpperCase();
        if (upper.contains("COURSE_QA")) {
            return IntentType.COURSE_QA;
        }
        if (upper.contains("SYSTEM_QUESTION")) {
            return IntentType.SYSTEM_QUESTION;
        }
        if (upper.contains("GREETING")) {
            return IntentType.GREETING;
        }
        return IntentType.UNKNOWN;
    }
}