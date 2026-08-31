package edu.zjut.traceqa.qaservice.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import edu.zjut.traceqa.common.model.po.SystemPrompt;
import edu.zjut.traceqa.qaservice.service.SystemPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 多 Agent 工厂（基于 Spring AI Alibaba Agent Framework）。
 *
 * <p>以 ReAct 智能体构建「意图识别」与「总结生成」两个核心 Agent，
 * 系统提示词由 {@link SystemPromptService} 动态加载，懒初始化保证
 * 初始化数据装载完成后才构建。所有构建失败均优雅降级为 null。</p>
 */
@Component
public class RagAgents {

    private static final Logger log = LoggerFactory.getLogger(RagAgents.class);

    private final ChatModel chatModel;
    private final SystemPromptService systemPromptService;

    private volatile ReactAgent intentAgent;
    private volatile ReactAgent answerAgent;

    public RagAgents(ChatModel chatModel, SystemPromptService systemPromptService) {
        this.chatModel = chatModel;
        this.systemPromptService = systemPromptService;
    }

    /**
     * 获取意图识别 Agent（懒加载缓存）
     */
    public ReactAgent intentAgent() {
        if (intentAgent == null) {
            synchronized (this) {
                if (intentAgent == null) {
                    intentAgent = buildAgent("intent-agent", "识别用户意图类型",
                            promptOrDefault("intent", "判断用户消息意图，输出 COURSE_QA/SYSTEM_QUESTION/GREETING/UNKNOWN 之一。"));
                }
            }
        }
        return intentAgent;
    }

    /**
     * 获取总结生成 Agent（懒加载缓存）
     */
    public ReactAgent answerAgent() {
        if (answerAgent == null) {
            synchronized (this) {
                if (answerAgent == null) {
                    answerAgent = buildAgent("answer-agent", "基于检索上下文生成课程回答",
                            promptOrDefault("summary", "基于检索上下文回答用户问题，使用 Markdown，引用标注 [citation:N]。"));
                }
            }
        }
        return answerAgent;
    }

    private ReactAgent buildAgent(String name, String description, String systemPrompt) {
        try {
            return ReactAgent.builder()
                    .name(name)
                    .description(description)
                    .systemPrompt(systemPrompt)
                    .model(chatModel)
                    .build();
        } catch (Exception e) {
            log.warn("Agent 构建失败（将走降级链路）：{}，err={}", name, e.getMessage());
            return null;
        }
    }

    private String promptOrDefault(String scenario, String defaultPrompt) {
        SystemPrompt prompt = systemPromptService.getActive(scenario);
        if (prompt != null && prompt.getContent() != null && !prompt.getContent().isBlank()) {
            return prompt.getContent();
        }
        return defaultPrompt;
    }
}