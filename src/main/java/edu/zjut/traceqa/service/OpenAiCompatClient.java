package edu.zjut.traceqa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.zjut.traceqa.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容接口的原始 HTTP 客户端（用于「自定义模型」）。
 *
 * <p>不依赖 Spring AI 的模型构建器，直接按 OpenAI Chat Completions 协议调用任意
 * OpenAI 兼容端点，支持同步与流式（SSE）两种模式。所有异常均返回空/友好提示，
 * 不向调用方抛底层异常。</p>
 */
@Slf4j
@Component
public class OpenAiCompatClient {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiCompatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 直接构建 WebClient（无连接器时回退 JDK HttpClient），避免依赖自动配置 Bean
        this.webClient = WebClient.builder().build();
    }

    /** 同步调用（非流式） */
    public String call(LlmConfig config, String systemPrompt, String userContent) {
        try {
            String body = webClient.post()
                    .uri(resolveUri(config) + "/chat/completions")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(buildRequest(config, systemPrompt, userContent, false))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return extractContent(body);
        } catch (Exception e) {
            log.warn("OpenAI 兼容调用失败：model={}, err={}", config.model(), e.getMessage());
            return null;
        }
    }

    /** 流式调用（SSE 增量输出） */
    public Flux<String> stream(LlmConfig config, String systemPrompt, String userContent) {
        return webClient.post()
                .uri(resolveUri(config) + "/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .bodyValue(buildRequest(config, systemPrompt, userContent, true))
                .retrieve()
                .bodyToFlux(String.class)
                // StringDecoder 会去除行分隔符与 "data: " 前缀，每个数据块即一条完整的 SSE 数据行
                .concatMap(chunk -> Flux.fromIterable(parseSse(chunk)))
                .onErrorResume(e -> {
                    log.warn("OpenAI 兼容流式调用失败：model={}, err={}", config.model(), e.getMessage());
                    return Flux.empty();
                });
    }

    /** 组装 Chat Completions 请求体 */
    private Map<String, Object> buildRequest(LlmConfig config, String systemPrompt, String userContent, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userContent));
        return Map.of(
                "model", config.model(),
                "messages", messages,
                "stream", stream
        );
    }

    /** 拼接 /chat/completions 完整地址 */
    private String resolveUri(LlmConfig config) {
        String base = config.baseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /** 从非流式响应中提取 content */
    private String extractContent(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("choices").path(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            log.warn("OpenAI 兼容响应解析失败：{}", body);
            return null;
        }
    }

    /** 解析 SSE 行，提取 delta.content（兼容带/不带 data: 前缀两种格式） */
    private List<String> parseSse(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
        if (data.equals("[DONE]")) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            String content = node.path("choices").path(0).path("delta").path("content").asText(null);
            return content == null || content.isEmpty() ? List.of() : List.of(content);
        } catch (Exception e) {
            return List.of();
        }
    }
}
