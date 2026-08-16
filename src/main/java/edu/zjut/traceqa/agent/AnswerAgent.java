package edu.zjut.traceqa.agent;

import jakarta.annotation.Resource;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import edu.zjut.traceqa.config.LlmConfig;
import edu.zjut.traceqa.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 总结生成 Agent（流式）。
 *
 * <p>默认基于 Spring AI Alibaba 的 ReAct 智能体；使用自定义模型时改走
 * {@link LlmService} 的 OpenAI 兼容流式直连。任何异常均优雅降级返回空流，
 * 由编排器继续走降级链路。</p>
 */
@Slf4j
@Component
public class AnswerAgent {

    @Resource
    private RagAgents ragAgents;

    @Resource
    private LlmService llmService;

    

    /**
     * 流式生成回答。
     *
     * @param userPrompt 组装好的「问题 + 检索上下文」提示词
     * @param config     自定义模型配置（null 表示使用默认模型）
     * @return 回答内容块流；失败返回空流
     */
    public Flux<String> streamAnswer(String userPrompt, LlmConfig config) {
        // 自定义模型：OpenAI 兼容直连
        if (config != null && config.isValid()) {
            return llmService.callStream("summary", userPrompt, config);
        }
        ReactAgent agent = ragAgents.answerAgent();
        if (agent == null) {
            log.debug("总结 Agent 未就绪，返回空流");
            return Flux.empty();
        }
        try {
            return agent.streamMessages(userPrompt)
                    .filter(m -> m.getText() != null && !m.getText().isEmpty())
                    .map(Message::getText)
                    .onErrorResume(e -> {
                        log.warn("总结 Agent 流式生成失败，返回空流：{}", e.getMessage());
                        return Flux.empty();
                    });
        } catch (Exception e) {
            log.warn("总结 Agent 调用异常，返回空流：{}", e.getMessage());
            return Flux.empty();
        }
    }
}