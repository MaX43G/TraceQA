package edu.zjut.traceqa.service;

import edu.zjut.traceqa.model.dto.LlmConfig;
import edu.zjut.traceqa.model.po.SystemPrompt;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * LLM 调用服务（统一封装熔断降级与模型路由）。
 *
 * <ul>
 *   <li>默认模型：走 Spring AI 自动配置的 ChatClient；</li>
 *   <li>服务端/自定义模型：走 {@link OpenAiCompatClient} 直连 OpenAI 兼容端点（仅本次请求，不持久化）；</li>
 *   <li>熔断打开时返回 {@code null} / 空流，走降级链路；</li>
 *   <li>系统提示词由 {@link SystemPromptService} 动态加载（数据库缺失时回退默认模板）。</li>
 * </ul>
 */
@Slf4j
@Service
public class LlmService {

    @Resource
    private ChatClient.Builder chatClientBuilder;
    @Resource
    private SystemPromptService systemPromptService;
    @Resource
    private CircuitBreakerService circuitBreakerService;
    @Resource
    private OpenAiCompatClient openAiCompatClient;

    private ChatClient chatClient;

    @PostConstruct
    private void init() {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 同步调用模型。
     *
     * @param scenario    系统提示词场景
     * @param userContent 用户输入
     * @param config      模型配置（null 表示使用默认模型）
     * @return 模型输出；熔断或失败时返回 null（调用方据此降级）
     */
    public String call(String scenario, String userContent, LlmConfig config) {
        if (!circuitBreakerService.allowRequest()) {
            log.debug("熔断打开，LLM 调用被短路：scenario={}", scenario);
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
            log.warn("LLM 调用失败：scenario={}, err={}", scenario, e.getMessage());
            return null;
        }
    }

    /**
     * 流式调用模型（SSE 打字机效果）。
     *
     * @param scenario    系统提示词场景
     * @param userContent 用户输入
     * @param config      模型配置（null 表示使用默认模型）
     * @return 内容块流；熔断或失败时返回空流
     */
    public Flux<String> callStream(String scenario, String userContent, LlmConfig config) {
        if (!circuitBreakerService.allowRequest()) {
            log.debug("熔断打开，LLM 流式调用被短路：scenario={}", scenario);
            return Flux.empty();
        }
        try {
            String systemPrompt = resolveSystemPrompt(scenario);
            if (config != null && config.isValid()) {
                return openAiCompatClient.stream(config, systemPrompt, userContent)
                        .filter(c -> c != null && !c.isEmpty())
                        .doOnError(e -> {
                            circuitBreakerService.recordFailure();
                            log.warn("LLM 流式调用失败：scenario={}, err={}", scenario, e.getMessage());
                        })
                        .doOnComplete(() -> circuitBreakerService.recordSuccess())
                        .onErrorResume(e -> Flux.empty());
            }
            return buildPrompt(systemPrompt).user(userContent)
                    .stream()
                    .content()
                    .filter(c -> c != null && !c.isEmpty())
                    .doOnError(e -> {
                        circuitBreakerService.recordFailure();
                        log.warn("LLM 流式调用失败：scenario={}, err={}", scenario, e.getMessage());
                    })
                    .doOnComplete(() -> circuitBreakerService.recordSuccess())
                    .onErrorResume(e -> Flux.empty());
        } catch (Exception e) {
            log.warn("LLM 流式调用异常：scenario={}, err={}", scenario, e.getMessage());
            return Flux.empty();
        }
    }

    /** 解析系统提示词内容（数据库缺失时由 Service 回退默认模板） */
    private String resolveSystemPrompt(String scenario) {
        SystemPrompt prompt = systemPromptService.getActive(scenario);
        if (prompt == null || prompt.getContent() == null) {
            return null;
        }
        return prompt.getContent();
    }

    /** 构造带系统提示词的 ChatClient 请求（提示词为空时仅使用用户输入） */
    private ChatClient.ChatClientRequestSpec buildPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return chatClient.prompt();
        }
        return chatClient.prompt().system(systemPrompt);
    }
}