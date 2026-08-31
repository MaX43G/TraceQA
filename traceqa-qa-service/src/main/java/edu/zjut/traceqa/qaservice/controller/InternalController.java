package edu.zjut.traceqa.qaservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.qaservice.service.CircuitBreakerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答服务内部接口（供管理服务等经 OpenFeign 调用，不经网关对外）。
 */
@Tag(name = "问答服务内部接口", description = "供微服务间调用，不经网关对外")
@RestController
@RequestMapping("/internal")
public class InternalController {

    @Resource
    private CircuitBreakerService circuitBreakerService;

    /**
     * 获取 LLM 熔断器状态
     */
    @Operation(summary = "获取 LLM 熔断器状态（内部调用）")
    @GetMapping("/circuit-breaker/state")
    public ApiResponse<String> circuitBreakerState() {
        return ApiResponse.ok(circuitBreakerService.currentState().name());
    }
}