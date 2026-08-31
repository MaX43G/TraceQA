package edu.zjut.traceqa.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 网关 Feign 客户端。
 *
 * <p>供管理服务调用，拉取网关聚合的 HTTP 请求运行指标（总请求数、延迟分位、
 * 状态/方法分布、Top 接口、慢请求、接口错误、最近异常日志等）。</p>
 */
@FeignClient(name = "traceqa-gateway")
public interface GatewayClient {

    /**
     * 获取网关请求运行指标。
     *
     * @return 网关指标键值对
     */
    @GetMapping("/internal/metrics")
    Map<String, Object> getMetrics();
}