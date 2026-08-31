package edu.zjut.traceqa.common.config;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LightRAG REST 客户端。
 *
 * <p>封装与 LightRAG Server 的全部交互：文档上传/状态/删除、检索（同步与 NDJSON 流式）、
 * 图谱与模型管理等。供知识库服务（入库）、问答服务（检索）与管理服务（监控）共享使用。</p>
 */
@Component
@EnableConfigurationProperties(LightRagProperties.class)
public class LightRagClient {

    private static final Logger log = LoggerFactory.getLogger(LightRagClient.class);

    private final LightRagProperties properties;
    private final ObjectMapper objectMapper;
    private RestClient restClient;
    private WebClient webClient;

    public LightRagClient(LightRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化 RestClient 与 WebClient
     */
    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeout()));
        RestClient.Builder builder = RestClient.builder();
        this.restClient = builder.baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", properties.getApiKey())
                .build();
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", properties.getApiKey())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024))
                        .build())
                .build();
    }

    /**
     * 上传文档（multipart），返回 track_id
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
            log.warn("LightRAG 上传失败：status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LightRAG 上传异常：{}", e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 健康探测
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
     * 查询任务状态
     */
    public Map<String, Object> queryTrackStatus(String trackId) {
        try {
            String body = restClient.get()
                    .uri("/documents/track_status/{trackId}", trackId)
                    .retrieve()
                    .body(String.class);
            return parseJson(body);
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 状态查询失败：trackId={}, status={}", trackId, e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG 状态查询异常：trackId={}, err={}", trackId, e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 删除文档记录
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
     * 同步检索（返回上下文）
     */
    public Map<String, Object> query(String query, String mode, boolean onlyContext) {
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
                            "top_k", properties.getTopK()))
                    .retrieve()
                    .body(String.class);
            return parseJson(body);
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 查询失败：mode={}, status={}，降级处理", mode, e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG 查询异常：mode={}, err={}", mode, e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 流式检索（NDJSON），返回引用列表
     */
    public List<Map<String, Object>> queryStream(String query, String mode, List<String> hlKeywords,
                                                 Consumer<String> progress) {
        List<Map<String, Object>> references = new ArrayList<>();
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
            body.put("top_k", properties.getTopK());
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
                    .block(Duration.ofSeconds(Math.max(30, properties.getReadTimeout() / 1000)));
        } catch (Exception e) {
            log.warn("LightRAG 流式查询降级：mode={}, err={}", mode, e.getMessage());
        }
        return references;
    }

    private void handleStreamLine(String line, Consumer<String> progress, List<Map<String, Object>> references) {
        if (line == null || line.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(line);
            if (node.has("progress") && progress != null) {
                String p = node.path("progress").asString("");
                if (!p.isBlank()) {
                    progress.accept("图谱引擎：" + p);
                }
            }
            JsonNode refs = node.path("references");
            if (refs.isArray()) {
                refs.forEach(r -> references.add(objectMapper.convertValue(r, new TypeReference<>() {
                })));
            }
        } catch (Exception e) {
        }
    }

    /**
     * 流水线状态
     */
    public Map<String, Object> getPipelineStatus() {
        return getJson("/documents/pipeline_status");
    }

    /**
     * 状态计数
     */
    public Map<String, Object> getStatusCounts() {
        return getJson("/documents/status_counts");
    }

    /**
     * 热门标签
     */
    public List<String> getPopularLabels(int limit) {
        try {
            String resp = restClient.get().uri("/graph/label/popular?limit=" + limit).retrieve().body(String.class);
            if (resp == null || resp.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(resp, new TypeReference<>() {
            });
        } catch (RestClientResponseException e) {
            log.warn("LightRAG 热门标签查询失败：status={}", e.getStatusCode());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        } catch (Exception e) {
            log.warn("LightRAG 热门标签查询异常：err={}", e.getMessage());
            throw new BizException(ErrorCode.LLM_UNAVAILABLE, "知识图谱服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 可用模型（Ollama 兼容）
     */
    public Map<String, Object> getModels() {
        return getJson("/api/tags");
    }

    /**
     * 运行中模型
     */
    public Map<String, Object> getRunningModels() {
        return getJson("/api/ps");
    }

    /**
     * 重试失败文档
     */
    public Map<String, Object> reprocessFailed() {
        return postJson("/documents/reprocess_failed", null);
    }

    /**
     * 清空缓存
     */
    public Map<String, Object> clearCache() {
        return postJson("/documents/clear_cache", Map.of());
    }

    /**
     * 取消流水线
     */
    public Map<String, Object> cancelPipeline() {
        return postJson("/documents/cancel_pipeline", null);
    }

    /**
     * 触发目录扫描
     */
    public Map<String, Object> scanDocuments() {
        return postJson("/documents/scan", null);
    }

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