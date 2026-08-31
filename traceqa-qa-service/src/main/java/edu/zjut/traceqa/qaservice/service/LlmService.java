package edu.zjut.traceqa.qaservice.service;

import edu.zjut.traceqa.common.model.dto.LlmConfig;
import edu.zjut.traceqa.common.model.po.SystemPrompt;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * LLM 调用服务。
 *
 * <p>统一封装三类调用：Spring AI ChatClient（默认模型）、OpenAI 兼容客户端
 * （自定义/服务端模型切换），并接入熔断降级。同步调用失败返回 null、流式失败返回空流。</p>
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final ChatClient.Builder chatClientBuilder;
    private final SystemPromptService systemPromptService;
    private final CircuitBreakerService circuitBreakerService;
    private final OpenAiCompatClient openAiCompatClient;
    private ChatClient chatClient;

    public LlmService(ChatClient.Builder chatClientBuilder, SystemPromptService systemPromptService,
                      CircuitBreakerService circuitBreakerService, OpenAiCompatClient openAiCompatClient) {
        this.chatClientBuilder = chatClientBuilder;
        this.systemPromptService = systemPromptService;
        this.circuitBreakerService = circuitBreakerService;
        this.openAiCompatClient = openAiCompatClient;
    }

    /**
     * 构建默认 ChatClient
     */
    @PostConstruct
    public void init() {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 同步调用。
     *
     * @param scenario    提示词场景
     * @param userContent 用户内容
     * @param config      自定义 LLM 配置（可为空，使用默认模型）
     * @return 回答文本；失败返回 null
     */
    public String call(String scenario, String userContent, LlmConfig config) {
        if (!circuitBreakerService.allowRequest()) {
            log.debug("LLM 熔断打开，拒绝请求");
            return null;
        }
        try {
            String systemPrompt = resolveSystemPrompt(scenario);
            if (config != null && config.isValid()) {
                return openAiCompatClient.call(config, systemPrompt, userContent);
            }
            String response = buildPrompt(systemPrompt).user(userContent).call().content();
            circuitBreakerService.recordSuccess();
            return response;
        } catch (Exception e) {
            circuitBreakerService.recordFailure();
            log.warn("LLM 同步调用失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式调用。
     *
     * @param scenario    提示词场景
     * @param userContent 用户内容
     * @param config      自定义 LLM 配置（可为空）
     * @return 内容增量流
     */
    public Flux<String> callStream(String scenario, String userContent, LlmConfig config) {
        if (!circuitBreakerService.allowRequest()) {
            log.debug("LLM 熔断打开，拒绝流式请求");
            return Flux.empty();
        }
        try {
            String systemPrompt = resolveSystemPrompt(scenario);
            if (config != null && config.isValid()) {
                return openAiCompatClient.stream(config, systemPrompt, userContent)
                        .filter(c -> !c.isEmpty())
                        .doOnError(_ -> circuitBreakerService.recordFailure())
                        .doOnComplete(circuitBreakerService::recordSuccess)
                        .onErrorResume(_ -> Flux.empty());
            }
            return buildPrompt(systemPrompt).user(userContent).stream().content()
                    .filter(c -> !c.isEmpty())
                    .doOnError(_ -> circuitBreakerService.recordFailure())
                    .doOnComplete(circuitBreakerService::recordSuccess)
                    .onErrorResume(_ -> Flux.empty());
        } catch (Exception e) {
            log.debug("LLM 流式调用异常：{}", e.getMessage());
            return Flux.empty();
        }
    }

    /**
     * 解析系统提示词（缺失返回 null，省略 system 消息）
     */
    private String resolveSystemPrompt(String scenario) {
        SystemPrompt prompt = systemPromptService.getActive(scenario);
        return prompt == null ? null : prompt.getContent();
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return chatClient.prompt();
        }
        return chatClient.prompt().system(systemPrompt);
    }
}