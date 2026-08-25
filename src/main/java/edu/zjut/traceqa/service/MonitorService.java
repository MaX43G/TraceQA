package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量运行指标采集（无外部中间件，内存 + Redis 计数）。
 *
 * <p>采集请求量/耗时、缓存命中、熔断状态、解析队列与最近异常日志，
 * 供管理员在前端「系统监控」页查看。用于排查限流、性能与稳定性问题。</p>
 */
@Slf4j
@Component
public class MonitorService {

    // ---- 请求指标 ----
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final Map<String, AtomicLong> pathCounts = new ConcurrentHashMap<>();

    // ---- 异常日志（环形缓冲，保留最近 50 条）----
    private final ConcurrentLinkedQueue<String> recentErrors = new ConcurrentLinkedQueue<>();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CircuitBreakerService circuitBreakerService;

    @Resource
    private DocumentQueueWorker documentQueueWorker;

    /** 请求开始时间（ThreadLocal） */
    private static final ThreadLocal<Long> REQUEST_START = new ThreadLocal<>();

    /** 记录请求开始（由 TraceIdFilter 调用） */
    public void startRequest(String path) {
        REQUEST_START.set(System.currentTimeMillis());
        totalRequests.incrementAndGet();
        pathCounts.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
    }

    /** 记录请求结束（由 TraceIdFilter 调用） */
    public void endRequest() {
        Long start = REQUEST_START.get();
        REQUEST_START.remove();
        if (start != null) {
            totalLatencyMs.addAndGet(System.currentTimeMillis() - start);
        }
    }

    // ---- 缓存命中（静态计数，避免与 RedisCacheService 循环依赖）----
    private static final AtomicLong CACHE_HITS = new AtomicLong();
    private static final AtomicLong CACHE_MISSES = new AtomicLong();

    /** 记录缓存命中（由 RedisCacheService 调用，静态） */
    public static void recordCacheHit() {
        CACHE_HITS.incrementAndGet();
    }

    /** 记录缓存未命中（由 RedisCacheService 调用，静态） */
    public static void recordCacheMiss() {
        CACHE_MISSES.incrementAndGet();
    }

    /** 记录异常日志（由全局异常处理器调用） */
    public void recordError(String message) {
        recentErrors.offer(message);
        if (recentErrors.size() > 50) {
            recentErrors.poll();
        }
    }

    /** 组装监控快照 */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        long total = totalRequests.get();

        // 请求量 + 平均延迟
        data.put("totalRequests", total);
        data.put("avgLatencyMs", total == 0 ? 0 : Math.round(totalLatencyMs.get() / (double) total));
        Map<String, Long> pathStats = new LinkedHashMap<>();
        pathCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(e -> pathStats.put(e.getKey(), e.getValue().get()));
        data.put("topPaths", pathStats);

        // 缓存命中率
        long hits = CACHE_HITS.get();
        long misses = CACHE_MISSES.get();
        long cacheTotal = hits + misses;
        data.put("cacheHits", hits);
        data.put("cacheMisses", misses);
        data.put("cacheHitRate", cacheTotal == 0 ? 0 : Math.round(hits * 100.0 / cacheTotal));

        // 熔断状态
        data.put("circuitBreaker", circuitBreakerService.currentState().name());

        // 解析队列
        data.put("queue", documentQueueWorker.queueStats());

        // 在线会话（Redis 中的 sa-token 活跃 token 数，粗略）
        data.put("activeSessions", countActiveSessions());

        // 最近异常
        data.put("recentErrors", java.util.List.copyOf(recentErrors));

        return data;
    }

    /** 统计 Redis 中活跃 sa-token 会话数（不精确，作参考） */
    private long countActiveSessions() {
        try {
            var keys = stringRedisTemplate.keys("satoken:login:token:*");
            return keys == null ? 0 : keys.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
