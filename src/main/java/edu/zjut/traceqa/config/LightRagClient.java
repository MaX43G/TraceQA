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
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
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
                .build();
    }

    /** 上传文件至 LightRAG 异步解析，返回 track_id */
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

    /** 查询文档解析状态（LightRAG 该版本路径为 /documents/track_status/{trackId}） */
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
     * @param query    检索文本
     * @param mode     查询模式（naive/local/hybrid/mix）
     * @param progress 检索进度回调（LightRAG 流水线步骤，可空）
     * @return 引用的原始列表（每个元素为 {reference_id,file_path,content:[...]}）
     */
    public List<Map<String, Object>> queryStream(String query, String mode, Consumer<String> progress) {
        List<Map<String, Object>> references = new ArrayList<>();
        // LightRAG 要求查询最短 3 字符，过短直接返回空，避免 422
        if (query == null || query.trim().length() < 3) {
            log.debug("查询过短，跳过 LightRAG 检索：query={}", query);
            return references;
        }
        try {
            webClient.post()
                    .uri("/query/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .bodyValue(Map.of(
                            "query", query,
                            "mode", mode,
                            "only_need_context", true,
                            "stream", true,
                            "include_progress", true,
                            "include_references", true,
                            "include_chunk_content", true,
                            "top_k", properties.getLightrag().getTopK()))
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

    /** 解析 /query/stream 的 NDJSON 行：progress 回调、references 收集 */
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
                        objectMapper.convertValue(r, new TypeReference<Map<String, Object>>() {
                        })));
            }
        } catch (Exception e) {
            // 单行解析失败忽略
        }
    }

    /** 组装 multipart 上传体 */
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

    /** 解析 JSON 响应体，解析失败视为服务异常 */
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