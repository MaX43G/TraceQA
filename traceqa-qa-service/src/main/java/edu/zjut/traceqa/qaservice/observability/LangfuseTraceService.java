package edu.zjut.traceqa.qaservice.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Langfuse 可观测性追踪服务。
 *
 * <p>通过 Langfuse HTTP API 记录 RAG 问答的完整链路追踪，
 * 包括 trace、span、event 和 generation（LLM 调用）。</p>
 *
 * <p>配置项（application.yaml）：</p>
 * <pre>
 * app:
 *   langfuse:
 *     enabled: true
 *     public-key: pk-xxx
 *     secret-key: sk-xxx
 *     host: https://cloud.langfuse.com
 * </pre>
 *
 * <p>数据采用批量异步上报，不阻塞主流程。</p>
 */
@Service
@ConditionalOnProperty(name = "app.langfuse.enabled", havingValue = "true")
public class LangfuseTraceService {

    private static final Logger log = LoggerFactory.getLogger(LangfuseTraceService.class);

    @Value("${app.langfuse.public-key:}")
    private String publicKey;

    @Value("${app.langfuse.secret-key:}")
    private String secretKey;

    @Value("${app.langfuse.host:https://cloud.langfuse.com}")
    private String host;

    @Value("${app.langfuse.batch-size:10}")
    private int batchSize;

    private RestTemplate restTemplate;
    private final ConcurrentLinkedQueue<Map<String, Object>> eventQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean initialized = false;

    @PostConstruct
    void init() {
        if (publicKey == null || publicKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("Langfuse 公钥或密钥未配置，追踪服务未启用");
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.initialized = true;
        log.info("Langfuse 追踪服务已初始化（host={}）", host);
    }

    public boolean isEnabled() {
        return initialized;
    }

    /**
     * 获取 Langfuse trace 的 Web UI 链接
     */
    public String getTraceUrl(String traceId) {
        if (!isEnabled() || traceId == null) return null;
        return host + "/trace/" + traceId;
    }

    // ========== Trace 操作 ==========

    /**
     * 创建新的追踪（trace），返回 traceId
     */
    public String createTrace(String name, Map<String, Object> metadata) {
        if (!isEnabled()) return UUID.randomUUID().toString();

        String traceId = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("id", traceId);
        body.put("name", name);
        body.put("timestamp", Instant.now().toString());
        if (metadata != null) {
            body.put("metadata", metadata);
        }
        enqueue("trace-create", body);
        return traceId;
    }

    /**
     * 更新 trace 的 metadata
     */
    public void updateTrace(String traceId, Map<String, Object> metadata) {
        if (!isEnabled()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("traceId", traceId);
        body.put("metadata", metadata);
        enqueue("trace-update", body);
    }

    // ========== Span 操作 ==========

    /**
     * 开始一个新的 span，返回 spanId
     */
    public String startSpan(String traceId, String name, String input) {
        if (!isEnabled()) return UUID.randomUUID().toString();

        String spanId = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("id", spanId);
        body.put("traceId", traceId);
        body.put("name", name);
        body.put("startTime", Instant.now().toString());
        if (input != null) {
            body.put("input", truncate(input));
        }
        enqueue("span-create", body);
        return spanId;
    }

    /**
     * 结束一个 span
     */
    public void endSpan(String spanId, String output, Map<String, Object> metadata) {
        if (!isEnabled()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("id", spanId);
        body.put("endTime", Instant.now().toString());
        if (output != null) {
            body.put("output", truncate(output));
        }
        if (metadata != null) {
            body.put("metadata", metadata);
        }
        enqueue("span-update", body);
    }

    /**
     * 标记 span 失败
     */
    public void failSpan(String spanId, String errorMessage) {
        if (!isEnabled()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("id", spanId);
        body.put("endTime", Instant.now().toString());
        body.put("statusMessage", errorMessage);
        Map<String, Object> level = new HashMap<>();
        level.put("level", "ERROR");
        body.put("level", level);
        enqueue("span-update", body);
    }

    // ========== Event 操作 ==========

    /**
     * 记录一个事件
     */
    public void recordEvent(String traceId, String name, Map<String, Object> data) {
        if (!isEnabled()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("traceId", traceId);
        body.put("name", name);
        body.put("startTime", Instant.now().toString());
        if (data != null) {
            body.put("metadata", data);
        }
        enqueue("event-create", body);
    }

    // ========== Generation 操作（LLM 调用） ==========

    /**
     * 记录 LLM generation 调用
     */
    public String startGeneration(String traceId, String name, String model, String input) {
        if (!isEnabled()) return UUID.randomUUID().toString();

        String generationId = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("id", generationId);
        body.put("traceId", traceId);
        body.put("name", name);
        body.put("startTime", Instant.now().toString());
        body.put("model", model);
        if (input != null) {
            body.put("input", truncate(input));
        }
        enqueue("generation-create", body);
        return generationId;
    }

    /**
     * 结束 LLM generation 调用
     */
    public void endGeneration(String generationId, String output, Long usageTokens,
                              Long promptTokens, Long completionTokens) {
        if (!isEnabled()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("id", generationId);
        body.put("endTime", Instant.now().toString());
        if (output != null) {
            body.put("output", truncate(output));
        }
        if (usageTokens != null || promptTokens != null || completionTokens != null) {
            Map<String, Object> usage = new HashMap<>();
            if (usageTokens != null) usage.put("totalTokens", usageTokens);
            if (promptTokens != null) usage.put("promptTokens", promptTokens);
            if (completionTokens != null) usage.put("completionTokens", completionTokens);
            body.put("usage", usage);
        }
        enqueue("generation-update", body);
    }

    // ========== 批量上报 ==========

    private void enqueue(String type, Map<String, Object> body) {
        body.put("__type", type);
        eventQueue.offer(body);
        if (eventQueue.size() >= batchSize) {
            flushBatch();
        }
    }

    /**
     * 批量上报事件到 Langfuse
     */
    public void flushBatch() {
        if (!isEnabled() || eventQueue.isEmpty()) return;

        List<Map<String, Object>> batch = new ArrayList<>();
        while (!eventQueue.isEmpty() && batch.size() < batchSize) {
            Map<String, Object> event = eventQueue.poll();
            if (event != null) {
                String type = (String) event.remove("__type");
                Map<String, Object> wrapped = new HashMap<>();
                wrapped.put("type", type);
                wrapped.put("body", event);
                batch.add(wrapped);
            }
        }

        if (batch.isEmpty()) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(publicKey, secretKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("batch", batch);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForObject(host + "/api/public/ingestion", entity, String.class);
            log.debug("Langfuse 上报 {} 条事件成功", batch.size());
        } catch (Exception e) {
            log.warn("Langfuse 上报失败：{}", e.getMessage());
            // 失败的事件放回队列
            batch.forEach(item -> {
                Map<String, Object> body = (Map<String, Object>) item.get("body");
                String type = (String) item.get("type");
                if (body != null) {
                    body.put("__type", type);
                    eventQueue.offer(body);
                }
            });
        }
    }

    /**
     * 定期刷新（建议在定时任务或请求结束时调用）
     */
    public void flush() {
        flushBatch();
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > 10000 ? text.substring(0, 10000) + "..." : text;
    }
}
