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

    /** 检索加速开关 */
    private Retrieval retrieval = new Retrieval();

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

    /**
     * 检索加速开关（默认关闭，优先速度）。
     *
     * <p>融合后的「二次检索补全」与「精排」均会引入额外 LLM 调用 / LightRAG 查询，显著增加耗时。
     * 默认关闭以提速；需要更高召回或排序质量时再开启。</p>
     */
    @Data
    @NoArgsConstructor
    public static class Retrieval {
        /** 是否执行 ReRead 二次检索补全（额外 1 次 LLM + 1 次 LightRAG 查询） */
        private boolean enableReread = false;
        /** 是否执行结果精排（外部语义重排；未配置时不回退 LLM 精排） */
        private boolean enableRerank = false;
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