package edu.zjut.traceqa.adminservice.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理服务配置属性。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AdminProperties {

    /**
     * 会话 Cookie 是否标记 Secure
     */
    private boolean cookieSecure = true;

    /**
     * 可观测性配置
     */
    private Observability observability = new Observability();

    /**
     * DBX 内网地址
     */
    private String dbxBaseUrl = "http://localhost:4224";

    /**
     * Nacos 配置
     */
    private NacosConfig nacos = new NacosConfig();

    /**
     * 可观测性配置
     */
    @Data
    @NoArgsConstructor
    public static class Observability {
        /**
         * Prometheus 抓取令牌
         */
        private String scrapeToken = "";
        /**
         * Grafana 内网地址
         */
        private String grafanaBaseUrl = "http://grafana:3000";
        /**
         * Prometheus 内网地址
         */
        private String prometheusBaseUrl = "http://prometheus:9090";
    }

    /**
     * Nacos 配置
     */
    @Data
    @NoArgsConstructor
    public static class NacosConfig {
        /**
         * Nacos Server 地址（内网）
         */
        private String serverAddr = "http://localhost:8848";
        /**
         * Nacos 访问令牌（可选，用于免密登录）
         */
        private String accessToken = "";
        /**
         * Cookie 过期时间（小时）
         */
        private int cookieExpireHours = 24;
    }
}