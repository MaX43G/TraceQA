package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量运行指标采集（无外部中间件，内存 + Redis 计数）。
 *
 * <p>采集请求量/耗时/分位延迟/慢请求、缓存命中、熔断状态、解析队列与最近异常日志，
 * 供管理员在前端「系统监控」页查看。用于排查限流、性能与稳定性问题。</p>
 */
@Slf4j
@Component
public class MonitorService {

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 2000L;
    /** 慢请求环形缓冲上限 */
    private static final int SLOW_REQUEST_MAX = 20;
    /** 延迟分位样本环形缓冲上限 */
    private static final int LATENCY_SAMPLES_MAX = 1000;
    /** 慢请求时间格式化 */
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // ---- 请求指标 ----
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final Map<String, AtomicLong> pathCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> methodCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> statusCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> pathErrorCounts = new ConcurrentHashMap<>();
    /** 延迟样本（环形缓冲，用于分位统计） */
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    /** 慢请求（环形缓冲，保留最近若干条） */
    private final ConcurrentLinkedQueue<Map<String, Object>> slowRequests = new ConcurrentLinkedQueue<>();

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
    public void startRequest(String path, String method) {
        REQUEST_START.set(System.currentTimeMillis());
        totalRequests.incrementAndGet();
        pathCounts.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
        methodCounts.computeIfAbsent(method, k -> new AtomicLong()).incrementAndGet();
    }

    /** 记录请求结束（由 TraceIdFilter 调用） */
    public void endRequest(String path, String method, int status, long costMs) {
        REQUEST_START.remove();
        totalLatencyMs.addAndGet(costMs);
        // 状态码桶（2xx/3xx/4xx/5xx）
        String bucket = (status / 100) + "xx";
        statusCounts.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
        // 错误率按路径统计
        if (status >= 400) {
            pathErrorCounts.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
        }
        // 延迟分位样本
        latencySamples.offer(costMs);
        while (latencySamples.size() > LATENCY_SAMPLES_MAX) {
            latencySamples.poll();
        }
        // 慢请求记录
        if (costMs >= SLOW_REQUEST_THRESHOLD_MS) {
            Map<String, Object> slow = new LinkedHashMap<>();
            slow.put("path", path);
            slow.put("method", method);
            slow.put("costMs", costMs);
            slow.put("status", status);
            slow.put("time", TIME_FMT.format(Instant.now()));
            slowRequests.offer(slow);
            while (slowRequests.size() > SLOW_REQUEST_MAX) {
                slowRequests.poll();
            }
        }
    }

    /** 计算延迟分位（P50/P95/P99），样本为空时返回 0 */
    private Map<String, Long> latencyPercentiles() {
        List<Long> sorted = new ArrayList<>(latencySamples);
        sorted.sort(Comparator.naturalOrder());
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("p50", percentile(sorted, 50));
        result.put("p95", percentile(sorted, 95));
        result.put("p99", percentile(sorted, 99));
        return result;
    }

    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    /** 汇总各 HTTP 方法计数 */
    private Map<String, Long> methodCountsMap() {
        Map<String, Long> out = new LinkedHashMap<>();
        methodCounts.entrySet().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .forEach(e -> out.put(e.getKey(), e.getValue().get()));
        return out;
    }

    /** 汇总各状态码桶 */
    private Map<String, Long> statusCounts() {
        Map<String, Long> out = new LinkedHashMap<>();
        statusCounts.entrySet().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .forEach(e -> out.put(e.getKey(), e.getValue().get()));
        return out;
    }

    /** 路径错误率（Top 10，仅含出现过错误的路径） */
    private Map<String, Long> pathErrors() {
        Map<String, Long> out = new LinkedHashMap<>();
        pathErrorCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(e -> out.put(e.getKey(), e.getValue().get()));
        return out;
    }

    /** JVM 运行时信息（进程级健康参考） */
    private Map<String, Object> runtime() {
        Runtime rt = Runtime.getRuntime();
        ThreadMXBean tm = ManagementFactory.getThreadMXBean();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        out.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        out.put("heapMaxMb", rt.maxMemory() / 1024 / 1024);
        out.put("threads", tm.getThreadCount());
        return out;
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

        // 请求量 + 平均延迟 + 分位延迟
        data.put("totalRequests", total);
        data.put("avgLatencyMs", total == 0 ? 0 : Math.round(totalLatencyMs.get() / (double) total));
        data.put("latencyPercentiles", latencyPercentiles());
        data.put("statusCounts", statusCounts());
        data.put("methodCounts", methodCountsMap());
        Map<String, Long> pathStats = new LinkedHashMap<>();
        pathCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(e -> pathStats.put(e.getKey(), e.getValue().get()));
        data.put("topPaths", pathStats);
        data.put("pathErrors", pathErrors());
        data.put("slowRequests", List.copyOf(slowRequests));

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

        // JVM 运行时
        data.put("runtime", runtime());

        // 最近异常
        data.put("recentErrors", List.copyOf(recentErrors));

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
