package edu.zjut.traceqa.common.client;

import edu.zjut.traceqa.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 知识库服务 Feign 客户端。
 *
 * <p>供管理服务等调用，获取文档解析任务队列的运行指标。</p>
 */
@FeignClient(name = "traceqa-kb-service")
public interface KbClient {

    /**
     * 获取文档解析任务队列统计（pending/dead/processing）。
     *
     * @return 统一响应，data 为队列指标键值对
     */
    @GetMapping("/internal/queue/stats")
    ApiResponse<Map<String, Object>> getQueueStats();
}