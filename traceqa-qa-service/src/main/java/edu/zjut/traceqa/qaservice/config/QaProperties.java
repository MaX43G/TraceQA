package edu.zjut.traceqa.qaservice.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 问答服务应用配置属性。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class QaProperties {

    /** 熔断降级参数 */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /** 语义重排参数 */
    private Rerank rerank = new Rerank();

    /** 可用模型列表 */
    private List<ModelItem> models = List.of(new ModelItem("默认模型", "", ""));

    /** 熔断器配置 */
    @Data
    @NoArgsConstructor
    public static class CircuitBreaker {
        private int failureThreshold = 3;
        private long openMillis = 30000;
        private int halfOpenMaxCalls = 1;
    }

    /** 语义重排配置 */
    @Data
    @NoArgsConstructor
    public static class Rerank {
        private boolean enabled = false;
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "BAAI/bge-reranker-v2-m3";
        private String path = "/rerank";
    }

    /** 可用模型条目 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelItem {
        private String name;
        private String model;
        private String baseUrl;
    }
}