package edu.zjut.traceqa.adminservice.service;

import edu.zjut.traceqa.common.client.GatewayClient;
import edu.zjut.traceqa.common.client.KbClient;
import edu.zjut.traceqa.common.client.QaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>在微服务架构下聚合各服务运行指标：经 OpenFeign 拉取网关请求指标（总请求/延迟分位/
 * 状态方法分布/Top 接口/慢请求/错误）、知识库队列统计、问答熔断与缓存命中率，
 * 经 Redis 统计活跃会话，本地采集 JVM 运行时信息，并汇总 LightRAG 引擎面板。</p>
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    /** sa-token 登录 token 的 Redis key 前缀：{tokenName}:login:token: */
    private static final String LOGIN_TOKEN_KEY_SUFFIX = ":login:token:";

    private final StringRedisTemplate stringRedisTemplate;
    private final GatewayClient gatewayClient;
    private final KbClient kbClient;
    private final QaClient qaClient;
    private final LightRagMonitorService lightRagMonitorService;

    /** sa-token 的 token-name（与 application.yaml 的 sa-token.token-name 一致） */
    @Value("${sa-token.token-name:Authorization}")
    private String tokenName;

    public MonitorService(StringRedisTemplate stringRedisTemplate, GatewayClient gatewayClient,
                          KbClient kbClient, QaClient qaClient, LightRagMonitorService lightRagMonitorService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gatewayClient = gatewayClient;
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
        // 网关请求指标：总请求数、平均延迟、延迟分位、状态/方法分布、Top 接口、慢请求、接口错误、最近异常日志
        data.putAll(gatewayMetrics());
        // 缓存命中率（来自问答服务）
        data.putAll(qaCacheStats());
        data.put("circuitBreaker", qaCircuitBreaker());
        data.put("queue", kbQueueStats());
        data.put("lightrag", lightRagMonitorService.snapshot());
        return data;
    }

    /**
     * 经 OpenFeign 获取网关聚合的请求运行指标
     */
    private Map<String, Object> gatewayMetrics() {
        try {
            var resp = gatewayClient.getMetrics();
            return resp == null ? Map.of() : resp;
        } catch (Exception e) {
            log.debug("获取网关请求指标失败：{}", e.getMessage());
            return Map.of("error", "网关指标不可用");
        }
    }

    /**
     * 经 OpenFeign 获取问答服务缓存命中统计
     */
    private Map<String, Object> qaCacheStats() {
        try {
            var resp = qaClient.getCacheStats();
            return resp == null || resp.getData() == null ? Map.of() : resp.getData();
        } catch (Exception e) {
            log.debug("获取缓存命中统计失败：{}", e.getMessage());
            return Map.of("cacheError", "问答服务不可用");
        }
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
     * 统计活跃会话（sa-token 登录 token 数量，近似）。
     * sa-token 将登录 token 存于 Redis key：{tokenName}:login:token:{tokenValue}。
     */
    private long countActiveSessions() {
        try {
            String pattern = tokenName + LOGIN_TOKEN_KEY_SUFFIX + "*";
            var keys = stringRedisTemplate.keys(pattern);
            return keys == null ? 0 : keys.size();
        } catch (Exception e) {
            log.debug("统计活跃会话失败：{}", e.getMessage());
            return 0;
        }
    }
}