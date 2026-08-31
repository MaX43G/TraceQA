package edu.zjut.traceqa.qaservice.service;

import edu.zjut.traceqa.common.model.dto.LlmConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 客户端（自定义/服务端模型切换用）。
 *
 * <p>直连任意 OpenAI 兼容服务（同步 {@code call} 与流式 {@code stream}），
 * 失败时优雅返回空，不向调用方抛异常。</p>
 */
@Component
public class OpenAiCompatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatClient.class);

    private final ObjectMapper objectMapper;
    private WebClient webClient;

    public OpenAiCompatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化 WebClient
     */
    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder().build();
    }

    /**
     * 同步调用，返回完整回答文本
     */
    public String call(LlmConfig config, String systemPrompt, String userContent) {
        try {
            String body = webClient.post()
                    .uri(resolveUri(config) + "/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(buildRequest(config, systemPrompt, userContent, false))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return extractContent(body);
        } catch (Exception e) {
            log.debug("OpenAI 兼容同步调用失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式调用，返回内容增量
     */
    public Flux<String> stream(LlmConfig config, String systemPrompt, String userContent) {
        try {
            return webClient.post()
                    .uri(resolveUri(config) + "/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .bodyValue(buildRequest(config, systemPrompt, userContent, true))
                    .retrieve()
                    .bodyToFlux(String.class)
                    .concatMap(chunk -> Flux.fromIterable(parseSse(chunk)))
                    .onErrorResume(e -> {
                        log.debug("OpenAI 兼容流式调用失败：{}", e.getMessage());
                        return Flux.empty();
                    });
        } catch (Exception e) {
            log.debug("OpenAI 兼容流式调用异常：{}", e.getMessage());
            return Flux.empty();
        }
    }

    private Map<String, Object> buildRequest(LlmConfig config, String systemPrompt, String userContent, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userContent));
        return Map.of("model", config.getModel(), "messages", messages, "stream", stream);
    }

    private List<String> parseSse(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : line.split("\n")) {
            String data = part.trim();
            if (data.startsWith("data:")) {
                data = data.substring(5).trim();
            }
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }
            try {
                String content = extractDelta(data);
                if (content != null && !content.isEmpty()) {
                    result.add(content);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String extractDelta(String json) {
        try {
            var node = objectMapper.readTree(json);
            var choice = node.path("choices").path(0);
            return choice.path("delta").path("content").asString("");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractContent(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(body);
            return node.path("choices").path(0).path("message").path("content").asString("");
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUri(LlmConfig config) {
        String base = config.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}