package edu.zjut.traceqa.common.client;

import edu.zjut.traceqa.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 问答服务 Feign 客户端。
 *
 * <p>供管理服务等调用，获取 LLM 熔断器运行状态等运行时指标。</p>
 */
@FeignClient(name = "traceqa-qa-service")
public interface QaClient {

    /**
     * 获取 LLM 熔断器状态（CLOSED/OPEN/HALF_OPEN）。
     *
     * @return 统一响应，data 为熔断器状态名
     */
    @GetMapping("/internal/circuit-breaker/state")
    ApiResponse<String> getCircuitBreakerState();
}