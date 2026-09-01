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
}