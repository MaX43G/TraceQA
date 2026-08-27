package edu.zjut.traceqa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LightRAG 官方 Server 的 REST 客户端。
 *
 * <p>基于 {@link RestClient} 封装图谱引擎的文档上传、解析状态查询与检索调用，
 * 统一处理 {@code X-API-Key} 鉴权头与错误降级。所有方法均捕获底层异常并抛
 * {@link BizException}，严禁将底层网络/堆栈异常透传至 Controller。</p>
 */
@Slf4j
@Component
public class LightRagClient {

    @Resource
    private RestClient.Builder builder;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private AppProperties properties;

    private RestClient restClient;
    private WebClient webClient;

    @PostConstruct
    private void init() {
        AppProperties.LightRag cfg = properties.getLightrag();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(cfg.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(cfg.getReadTimeout()));
        this.restClient = builder.baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", cfg.getApiKey())
                .build();
        // 流式查询使用 WebClient（NDJSON）
        this.webClient = WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("X-API-Key", cfg.getApiKey())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024))
                        .build())
                .build();
    }

    /**
     * 上传文件至 LightRAG 异步解析，返回 track_id
     */
    public String uploadDocument(byte[] content, String filename) {
        try {
            String body = restClient.post()
                    .uri("/documents/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(buildUploadBody(content, filename))
                    .retrieve()
                    .body(String.class);
            Map<String, Object> json = parseJson(body);
            Object trackId = json.get("track_id");
            if (trackId == null || String.valueOf(trackId).isBlank()) {
                throw new BizException(ErrorCode.FILE_ERROR, "LightRAG 未返回任务标识");
            }
            return String.valueOf(trackId);
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 上传失败：status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LightRAG 上传异常：{}", e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 健康探测：LightRAG /health 是否可达
     */
    public boolean ping() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("LightRAG 健康探测失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 查询文档解析状态（LightRAG 该版本路径为 /documents/track_status/{trackId}）
     */
    public Map<String, Object> queryTrackStatus(String trackId) {
        try {
            String body = restClient.get()
                    .uri("/documents/track_status/{trackId}", trackId)
                    .retrieve()
                    .body(String.class);
            return parseJson(body);
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 状态查询失败：trackId={}, status={}",
                    trackId, e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG 状态查询异常：trackId={}, err={}", trackId, e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 删除 LightRAG 中的文档记录（解析失败重试前清理，避免内容去重拦截）
     */
    public void deleteDocument(String docId) {
        try {
            restClient.delete().uri("/documents/{docId}", docId).retrieve();
            log.info("已清理 LightRAG 失败记录：docId={}", docId);
        } catch (Exception e) {
            log.warn("LightRAG 文档删除失败：docId={}, err={}", docId, e.getMessage());
        }
    }

    /**
     * 执行检索查询。
     *
     * @param query       检索文本
     * @param mode        查询模式（naive 向量 / local 局部图 / global 全局图 / hybrid / mix 融合）
     * @param onlyContext 是否仅返回检索上下文（true 时不触发 LightRAG 自身 LLM 生成）
     * @return 响应体 JSON
     */
    public Map<String, Object> query(String query, String mode, boolean onlyContext) {
        // LightRAG 要求查询最短 3 字符，过短直接返回空，避免 422
        if (query == null || query.trim().length() < 3) {
            return Map.of();
        }
        try {
            String body = restClient.post()
                    .uri("/query")
                    .body(Map.of(
                            "query", query,
                            "mode", mode,
                            "only_need_context", onlyContext,
                            "include_references", true,
                            "include_chunk_content", true,
                            "top_k", properties.getLightrag().getTopK()))
                    .retrieve()
                    .body(String.class);
            return parseJson(body);
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 查询失败：mode={}, status={}，降级处理",
                    mode, e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG 查询异常：mode={}, err={}", mode, e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 流式检索查询（调用 /query/stream，实时回调检索进度并收集引用）。
     *
     * @param query      检索文本
     * @param mode       查询模式（naive/local/hybrid/mix）
     * @param hlKeywords 关键词检索（高优先级关键词，可空）——用于「关键词路」三路混合检索
     * @param progress   检索进度回调（LightRAG 流水线步骤，可空）
     * @return 引用的原始列表（每个元素为 {reference_id,file_path,content:[...]}）
     */
    public List<Map<String, Object>> queryStream(String query, String mode, List<String> hlKeywords,
                                                 Consumer<String> progress) {
        List<Map<String, Object>> references = new ArrayList<>();
        // LightRAG 要求查询最短 3 字符，过短直接返回空，避免 422
        if (query == null || query.trim().length() < 3) {
            log.debug("查询过短，跳过 LightRAG 检索：query={}", query);
            return references;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("mode", mode);
            body.put("only_need_context", true);
            body.put("stream", true);
            body.put("include_progress", true);
            body.put("include_references", true);
            body.put("include_chunk_content", true);
            body.put("top_k", properties.getLightrag().getTopK());
            if (hlKeywords != null && !hlKeywords.isEmpty()) {
                body.put("hl_keywords", hlKeywords);
            }
            webClient.post()
                    .uri("/query/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(line -> handleStreamLine(line, progress, references))
                    .then()
                    .block(Duration.ofSeconds(Math.max(30, properties.getLightrag().getReadTimeout() / 1000)));
        } catch (Exception e) {
            log.warn("LightRAG 流式查询降级：mode={}, err={}", mode, e.getMessage());
        }
        return references;
    }

    /**
     * 解析 /query/stream 的 NDJSON 行：progress 回调、references 收集
     */
    private void handleStreamLine(String line, Consumer<String> progress, List<Map<String, Object>> references) {
        if (line == null || line.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(line);
            if (node.has("progress") && progress != null) {
                String p = node.path("progress").asText("");
                if (!p.isBlank()) {
                    progress.accept("图谱引擎：" + p);
                }
            }
            JsonNode refs = node.path("references");
            if (refs.isArray()) {
                refs.forEach(r -> references.add(
                        objectMapper.convertValue(r, new TypeReference<>() {
                        })));
            }
        } catch (Exception e) {
            // 单行解析失败忽略
        }
    }

    /**
     * 查询文档索引流水线状态（是否繁忙、批次进度、是否需恢复等）
     */
    public Map<String, Object> getPipelineStatus() {
        return getJson("/documents/pipeline_status");
    }

    /**
     * 查询文档按状态统计（PENDING/PROCESSING/PREPROCESSED/PROCESSED/FAILED）
     */
    public Map<String, Object> getStatusCounts() {
        return getJson("/documents/status_counts");
    }

    /**
     * 查询图谱热门标签（按节点度排序，最连通的实体）。LightRAG 返回 JSON 数组，如 ["MySQL","Person",...]
     */
    public List<String> getPopularLabels(int limit) {
        try {
            String resp = restClient.get()
                    .uri("/graph/label/popular?limit=" + limit)
                    .retrieve()
                    .body(String.class);
            if (resp == null || resp.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(resp, new TypeReference<>() {
            });
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 热门标签查询失败：status={}", e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG 热门标签查询异常：uri=/graph/label/popular, err={}", e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 查询以指定标签为起点、包含该标签的连通子图（知识图谱路径可视化用）。
     * LightRAG 返回 {@code {nodes:[...], edges:[...], is_truncated:bool}}。
     */
    public Map<String, Object> getGraph(String label, int maxDepth, int maxNodes) {
        try {
            String encoded = org.springframework.web.util.UriUtils.encodePathSegment(label, java.nio.charset.StandardCharsets.UTF_8);
            return getJson("/graphs?label=" + encoded + "&max_depth=" + maxDepth + "&max_nodes=" + maxNodes);
        } catch (Exception e) {
            log.warn("LightRAG 图谱查询失败：label={}, err={}", label, e.getMessage());
            return Map.of("nodes", List.of(), "edges", List.of());
        }
    }

    /**
     * 查询 LightRAG 可用模型（Ollama 兼容 /api/tags）
     */
    public Map<String, Object> getModels() {
        return getJson("/api/tags");
    }

    /**
     * 查询当前加载运行的模型（Ollama 兼容 /api/ps）
     */
    public Map<String, Object> getRunningModels() {
        return getJson("/api/ps");
    }

    /**
     * 重试 LightRAG 中解析失败的文档
     */
    public Map<String, Object> reprocessFailed() {
        return postJson("/documents/reprocess_failed", null);
    }

    /**
     * 清空 LightRAG 缓存
     */
    public Map<String, Object> clearCache() {
        return postJson("/documents/clear_cache", Map.of());
    }

    /**
     * 取消当前运行的索引流水线
     */
    public Map<String, Object> cancelPipeline() {
        return postJson("/documents/cancel_pipeline", null);
    }

    /**
     * 触发 LightRAG 目录扫描
     */
    public Map<String, Object> scanDocuments() {
        return postJson("/documents/scan", null);
    }

    /**
     * 发送 GET 请求并解析 JSON（失败统一抛 BizException）
     */
    private Map<String, Object> getJson(String uri) {
        try {
            return parseJson(restClient.get().uri(uri).retrieve().body(String.class));
        } catch (RestClientResponseException e) {
            log.warn("LightRAG GET 失败：uri={}, status={}", uri, e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG GET 异常：uri={}, err={}", uri, e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 发送 POST 请求并解析 JSON（失败统一抛 BizException）
     */
    private Map<String, Object> postJson(String uri, Object body) {
        try {
            String resp;
            if (body == null) {
                resp = restClient.post().uri(uri).retrieve().body(String.class);
            } else {
                resp = restClient.post().uri(uri).body(body).retrieve().body(String.class);
            }
            return parseJson(resp);
        } catch (RestClientResponseException e) {
            log.warn("LightRAG POST 失败：uri={}, status={}", uri, e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG POST 异常：uri={}, err={}", uri, e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 组装 multipart 上传体
     */
    private MultiValueMap<String, Object> buildUploadBody(byte[] content, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        form.add("file", resource);
        return form;
    }

    /**
     * 解析 JSON 响应体，解析失败视为服务异常
     */
    private Map<String, Object> parseJson(String body) {
        if (body == null || body.isBlank()) {
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务返回异常");
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("LightRAG 响应解析失败：{}", body);
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务返回异常");
        }
    }
}