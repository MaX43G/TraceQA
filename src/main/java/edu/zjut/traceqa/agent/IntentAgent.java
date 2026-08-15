package edu.zjut.traceqa.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import edu.zjut.traceqa.common.enums.IntentType;
import edu.zjut.traceqa.config.LlmConfig;
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

    private final RagAgents ragAgents;
    private final LlmService llmService;

    public IntentAgent(RagAgents ragAgents, LlmService llmService) {
        this.ragAgents = ragAgents;
        this.llmService = llmService;
    }

    /** 默认模型意图识别 */
    public IntentType identify(String message) {
        return identify(message, null);
    }

    /**
     * 识别用户消息意图。
     *
     * @param message 用户消息
     * @param config  自定义模型配置（null 表示使用默认模型）
     * @return 意图类型（失败返回 UNKNOWN）
     */
    public IntentType identify(String message, LlmConfig config) {
        // 自定义模型：直接走 OpenAI 兼容直连
        if (config != null && config.isValid()) {
            String text = llmService.call("intent", message, config);
            return parseIntent(text);
        }
        ReactAgent agent = ragAgents.intentAgent();
        if (agent == null) {
            log.debug("意图 Agent 未就绪，降级为 UNKNOWN");
            return IntentType.UNKNOWN;
        }
        try {
            String text = agent.call(message).getText();
            return parseIntent(text);
        } catch (Exception e) {
            log.warn("意图识别失败，降级为 UNKNOWN：{}", e.getMessage());
            return IntentType.UNKNOWN;
        }
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