package edu.zjut.traceqa.qaservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.qaservice.service.CircuitBreakerService;
import edu.zjut.traceqa.qaservice.service.RedisCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 问答服务内部接口（供管理服务等经 OpenFeign 调用，不经网关对外）。
 */
@Tag(name = "问答服务内部接口", description = "供微服务间调用，不经网关对外")
@RestController
@RequestMapping("/internal")
public class InternalController {

    @Resource
    private CircuitBreakerService circuitBreakerService;

    @Resource
    private RedisCacheService redisCacheService;

    /**
     * 获取 LLM 熔断器状态
     */
    @Operation(summary = "获取 LLM 熔断器状态（内部调用）")
    @GetMapping("/circuit-breaker/state")
    public ApiResponse<String> circuitBreakerState() {
        return ApiResponse.ok(circuitBreakerService.currentState().name());
    }

    /**
     * 获取缓存命中统计
     */
    @Operation(summary = "获取缓存命中统计（内部调用）")
    @GetMapping("/cache/stats")
    public ApiResponse<Map<String, Object>> cacheStats() {
        return ApiResponse.ok(redisCacheService.cacheStats());
    }
}