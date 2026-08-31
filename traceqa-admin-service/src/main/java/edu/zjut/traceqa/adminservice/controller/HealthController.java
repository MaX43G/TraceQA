package edu.zjut.traceqa.adminservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.client.KbClient;
import edu.zjut.traceqa.common.config.LightRagClient;
import edu.zjut.traceqa.adminservice.mapper.AnnouncementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 健康检查接口。
 *
 * <p>供 docker-compose 健康检查与运维探测使用，无需鉴权。
 * 聚合返回 Redis、MySQL、LightRAG 与文档解析队列状态。</p>
 */
@Tag(name = "健康检查", description = "系统健康检查（公开）")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AnnouncementMapper announcementMapper;
    @Resource
    private LightRagClient lightRagClient;
    @Resource
    private KbClient kbClient;

    /**
     * 健康检查
     */
    @Operation(summary = "健康检查")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "traceqa");
        data.put("redis", redisStatus());
        data.put("mysql", mysqlStatus());
        data.put("lightrag", lightRagClient.ping() ? "UP" : "DOWN");
        data.put("queue", queueStats());
        return ApiResponse.ok(data);
    }

    private String redisStatus() {
        try {
            Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection().ping();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String mysqlStatus() {
        try {
            announcementMapper.selectCount(null);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private Map<String, Object> queueStats() {
        try {
            var resp = kbClient.getQueueStats();
            return resp == null ? Map.of() : resp.getData();
        } catch (Exception e) {
            return Map.of("error", "知识库服务不可用");
        }
    }
}