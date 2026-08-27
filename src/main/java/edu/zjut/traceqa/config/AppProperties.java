package edu.zjut.traceqa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 应用自定义配置（绑定 {@code app.*} 前缀）。
 *
 * <p>集中管理文件存储根目录与 JWT 密钥等，避免在各 Service 中散落魔法字符串。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 文件存储配置 */
    private Storage storage = new Storage();

    /** JWT 配置 */
    private Jwt jwt = new Jwt();

    /** LightRAG 配置 */
    private LightRag lightrag = new LightRag();

    /** 熔断降级配置 */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /** 可观测性配置 */
    private Observability observability = new Observability();

    /** 会话 Cookie 是否标记 Secure（HTTPS 部署建议开启） */
    private boolean cookieSecure = false;

    /** 语义重排（Rerank）配置 */
    private Rerank rerank = new Rerank();

    /** 可用模型列表（供前端模型选择，后续可扩展多个模型） */
    private List<ModelItem> models = List.of(
            new ModelItem("默认模型", "", ""));

    /** 本地文件存储 */
    @Data
    public static class Storage {
        /** 存储根目录 */
        private String root = "./data/files";
    }

    /** JWT */
    @Data
    public static class Jwt {
        /** 签名密钥 */
        private String secret = "traceqa-secret-key-please-change-in-prod-2026";
        /** 有效期（毫秒） */
        private long expiration = 7200000;
    }

    /** LightRAG */
    @Data
    public static class LightRag {
        /** Server 地址 */
        private String baseUrl = "http://localhost:9621";
        /** API 密钥（X-API-Key 请求头） */
        private String apiKey = "";
        /** 检索条数 */
        private int topK = 20;
        /** 建连超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 60000;
    }

    /** 熔断降级 */
    @Data
    public static class CircuitBreaker {
        /** 连续失败阈值，超过则熔断 */
        private int failureThreshold = 3;
        /** 熔断打开时长（毫秒） */
        private long openMillis = 30000;
        /** 半开状态允许的最大试探调用数 */
        private int halfOpenMaxCalls = 1;
    }

    /** 可观测性（Actuator / Prometheus） */
    @Data
    public static class Observability {
        /** Prometheus 抓取令牌（需与抓取方配置一致；为空则不开放 /actuator/prometheus 抓取） */
        private String scrapeToken = "";
        /** Grafana 内网地址（经后端 /grafana/** 反向代理） */
        private String grafanaBaseUrl = "http://grafana:3000";
        /** Prometheus 内网地址（经后端 /prometheus/** 反向代理） */
        private String prometheusBaseUrl = "http://prometheus:9090";
    }

/** 模型条目（模型选择功能） */
    @Data
    public static class ModelItem {
        /** 展示名称 */
        private String name;
        /** 模型标识（如 THUDM/GLM-Z1-9B-0414） */
        private String model;
        /** OpenAI 兼容地址（默认模型为空串表示使用 spring.ai 配置） */
        private String baseUrl;

        public ModelItem() {
        }

        public ModelItem(String name, String model, String baseUrl) {
            this.name = name;
            this.model = model;
            this.baseUrl = baseUrl;
        }
    }

    /** 语义重排（Rerank） */
    @Data
    public static class Rerank {
        /** 是否启用语义重排 */
        private boolean enabled = false;
        /** Rerank 服务地址（如硅基流动的 rerank 端点） */
        private String baseUrl = "";
        /** API Key（可为空） */
        private String apiKey = "";
        /** 重排模型 */
        private String model = "BAAI/bge-reranker-v2-m3";
        /** Rerank 接口路径 */
        private String path = "/rerank";
    }
}