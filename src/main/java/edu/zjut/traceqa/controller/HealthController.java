package edu.zjut.traceqa.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.mapper.DocumentMapper;
import edu.zjut.traceqa.service.DocumentService;
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
 * 聚合返回后端、Redis、MySQL、LightRAG 与解析队列状态，便于快速定位降级原因。</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private LightRagClient lightRagClient;

    @Resource
    private DocumentService documentService;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "traceqa");
        data.put("redis", redisStatus());
        data.put("mysql", mysqlStatus());
        data.put("lightrag", lightRagClient.ping() ? "UP" : "DOWN");
        data.put("queue", documentService.queueStats());
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
            documentMapper.selectCount(null);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
