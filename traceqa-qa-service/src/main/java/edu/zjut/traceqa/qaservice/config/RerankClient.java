package edu.zjut.traceqa.qaservice.config;

import edu.zjut.traceqa.qaservice.config.QaProperties.Rerank;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 外部语义重排客户端（可选，如 SiliconFlow 的 bge-reranker）。
 *
 * <p>未配置或调用失败时返回 null，调用方回退到 LLM 精排。</p>
 */
@Component
public class RerankClient {

    private static final Logger log = LoggerFactory.getLogger(RerankClient.class);

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final QaProperties properties;
    private RestClient restClient;

    public RerankClient(RestClient.Builder builder, ObjectMapper objectMapper, QaProperties properties) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 初始化 RestClient（未配置 baseUrl 则跳过）
     */
    @PostConstruct
    public void init() {
        String baseUrl = properties.getRerank().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * 语义重排，返回按相关度从高到低排列的文档索引顺序。
     *
     * @return 重排后的索引顺序；未启用/未配置/失败返回 null
     */
    public List<Integer> rerank(String query, List<String> documents) {
        Rerank cfg = properties.getRerank();
        if (!cfg.isEnabled() || restClient == null || documents == null || documents.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> body = Map.of("model", cfg.getModel(), "query", query == null ? "" : query,
                    "documents", documents, "top_n", documents.size());
            String resp = restClient.post().uri(cfg.getPath()).body(body).retrieve().body(String.class);
            if (resp == null || resp.isBlank()) {
                return null;
            }
            var root = objectMapper.readTree(resp);
            List<Map<String, Object>> results = objectMapper.convertValue(root.path("results"), new TypeReference<>() {
            });
            List<Integer> order = new ArrayList<>();
            results.stream()
                    .sorted(Comparator.comparingDouble(
                            r -> -((Number) r.get("relevance_score")).doubleValue()))
                    .forEach(r -> order.add((Integer) r.get("index")));
            return order.isEmpty() ? null : order;
        } catch (Exception e) {
            log.debug("语义重排失败，回退 LLM 精排：{}", e.getMessage());
            return null;
        }
    }
}