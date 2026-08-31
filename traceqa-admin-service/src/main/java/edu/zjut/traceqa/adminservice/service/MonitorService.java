package edu.zjut.traceqa.adminservice.service;

import edu.zjut.traceqa.common.client.KbClient;
import edu.zjut.traceqa.common.client.QaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统监控服务。
 *
 * <p>在微服务架构下聚合各服务运行指标：经 OpenFeign 拉取知识库服务队列统计与
 * 问答服务熔断状态，经 Redis 统计活跃会话，本地采集 JVM 运行时信息，
 * 并汇总 LightRAG 引擎面板。</p>
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final KbClient kbClient;
    private final QaClient qaClient;
    private final LightRagMonitorService lightRagMonitorService;

    public MonitorService(StringRedisTemplate stringRedisTemplate, KbClient kbClient,
                          QaClient qaClient, LightRagMonitorService lightRagMonitorService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.kbClient = kbClient;
        this.qaClient = qaClient;
        this.lightRagMonitorService = lightRagMonitorService;
    }

    /**
     * 监控快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runtime", runtime());
        data.put("activeSessions", countActiveSessions());
        data.put("queue", kbQueueStats());
        data.put("circuitBreaker", qaCircuitBreaker());
        data.put("lightrag", lightRagMonitorService.snapshot());
        return data;
    }

    /**
     * JVM 运行时信息
     */
    private Map<String, Object> runtime() {
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("uptimeSeconds", runtimeMx.getUptime() / 1000);
        info.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        info.put("heapMaxMb", runtime.maxMemory() / 1024 / 1024);
        info.put("threads", threadMx.getThreadCount());
        return info;
    }

    /**
     * 经 OpenFeign 获取知识库队列统计
     */
    private Map<String, Object> kbQueueStats() {
        try {
            var resp = kbClient.getQueueStats();
            return resp == null ? Map.of() : resp.getData();
        } catch (Exception e) {
            log.debug("获取知识库队列统计失败：{}", e.getMessage());
            return Map.of("error", "知识库服务不可用");
        }
    }

    /**
     * 经 OpenFeign 获取 LLM 熔断状态
     */
    private String qaCircuitBreaker() {
        try {
            var resp = qaClient.getCircuitBreakerState();
            return resp == null ? "UNKNOWN" : resp.getData();
        } catch (Exception e) {
            log.debug("获取问答熔断状态失败：{}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * 统计活跃会话（sa-token 登录 token 数量，近似）
     */
    private long countActiveSessions() {
        try {
            var keys = stringRedisTemplate.keys("satoken:login:token:*");
            return keys == null ? 0 : keys.size();
        } catch (Exception e) {
            return 0;
        }
    }
}