package edu.zjut.traceqa.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语义重排（Rerank）客户端。
 *
 * <p>调用外部 Rerank 服务（如硅基流动的 BAAI/bge-reranker-v2-m3）对检索片段按相关度重新排序，
 * 优于传统的 LLM 精排。请求/响应为常见 Rerank 格式：
 * {@code POST {base}/rerank} body {@code {model, query, documents, top_n}}，
 * 响应 {@code {results:[{index, relevance_score}]}}。未配置或失败时返回 {@code null}，
 * 由调用方回退到原 LLM 精排。</p>
 */
@Slf4j
@Component
public class RerankClient {

    @Resource
    private RestClient.Builder builder;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AppProperties properties;

    private RestClient restClient;

    @PostConstruct
    private void init() {
        AppProperties.Rerank cfg = properties.getRerank();
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            return;
        }
        RestClient.Builder rb = builder.baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            rb.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey());
        }
        this.restClient = rb.build();
    }

    /**
     * 对文档片段进行重排。
     *
     * @param query     检索问题
     * @param documents 待重排的片段内容（顺序与待重排片段一致）
     * @return 按相关度降序的片段原始索引列表；未启用/失败/无法解析返回 {@code null}
     */
    public List<Integer> rerank(String query, List<String> documents) {
        AppProperties.Rerank cfg = properties.getRerank();
        if (!cfg.isEnabled() || restClient == null || documents == null || documents.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", cfg.getModel(),
                    "query", query == null ? "" : query,
                    "documents", documents,
                    "top_n", documents.size());
            String resp = restClient.post()
                    .uri(cfg.getPath())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (resp == null || resp.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(resp);
            JsonNode results = node.path("results");
            if (!results.isArray()) {
                return null;
            }
            List<JsonNode> list = new ArrayList<>();
            results.forEach(list::add);
            list.sort((a, b) -> Double.compare(
                    b.path("relevance_score").asDouble(0),
                    a.path("relevance_score").asDouble(0)));
            List<Integer> order = new ArrayList<>();
            for (JsonNode r : list) {
                int idx = r.path("index").asInt(-1);
                if (idx >= 0) {
                    order.add(idx);
                }
            }
            return order.isEmpty() ? null : order;
        } catch (Exception e) {
            log.warn("Rerank 调用失败，回退 LLM 精排：{}", e.getMessage());
            return null;
        }
    }
}