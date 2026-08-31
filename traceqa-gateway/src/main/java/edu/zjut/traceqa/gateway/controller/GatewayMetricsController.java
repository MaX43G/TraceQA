package edu.zjut.traceqa.gateway.controller;

import edu.zjut.traceqa.gateway.metric.GatewayMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 网关内部指标接口（供管理服务经 OpenFeign 拉取，不经网关对外）。
 */
@RestController
@RequestMapping("/internal")
public class GatewayMetricsController {

    private final GatewayMetrics metrics;

    public GatewayMetricsController(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    /** 返回网关聚合的请求运行指标 */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.snapshot();
    }
}