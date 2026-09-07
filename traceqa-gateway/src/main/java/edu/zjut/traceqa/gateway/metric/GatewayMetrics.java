package edu.zjut.traceqa.gateway.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关请求指标采集器（Micrometer 增强版）。
 *
 * <p>同时保留内存态快照（供管理服务 API 查询）和 Micrometer 指标（供 Prometheus/Grafana 可观测性）。</p>
 *
 * <p>Prometheus 指标：</p>
 * <ul>
 *   <li>{@code gateway.requests.total} — 按 method+status 标签的请求计数</li>
 *   <li>{@code gateway.request.latency} — 按 path 标签的请求延迟 Timer</li>
 *   <li>{@code gateway.requests.slow} — 慢请求计数（>2s）</li>
 *   <li>{@code gateway.requests.error} — 错误请求计数（>=400）</li>
 * </ul>
 */
@Component
public class GatewayMetrics {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final long SLOW_REQUEST_THRESHOLD_MS = 2000L;
    private static final int SLOW_REQUEST_MAX = 20;
    private static final int LATENCY_SAMPLES_MAX = 1000;
    private static final int RECENT_ERRORS_MAX = 50;

    @Resource
    private MeterRegistry meterRegistry;

    private Timer requestTimer;
    private Counter slowRequestCounter;
    private DistributionSummary latencySummary;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();

    private final ConcurrentMap<String, AtomicLong> statusCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> methodCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> pathCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> pathErrorCounts = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> slowRequests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> recentErrors = new ConcurrentLinkedQueue<>();

    @PostConstruct
    void init() {
        requestTimer = Timer.builder("gateway.request.latency")
                .description("Gateway request latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        slowRequestCounter = Counter.builder("gateway.requests.slow")
                .description("Requests exceeding slow threshold (>2s)")
                .register(meterRegistry);
        latencySummary = DistributionSummary.builder("gateway.latency.distribution")
                .description("Request latency distribution in ms")
                .baseUnit("ms")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 记录一次请求的指标。
     *
     * @param path   请求路径
     * @param method 请求方法
     * @param status 响应状态码
     * @param costMs 耗时（毫秒）
     */
    public void record(String path, String method, int status, long costMs) {
        totalRequests.incrementAndGet();
        totalLatencyMs.addAndGet(costMs);

        String statusGroup = (status / 100) + "xx";
        statusCounts.computeIfAbsent(statusGroup, _ -> new AtomicLong()).incrementAndGet();
        methodCounts.computeIfAbsent(method, _ -> new AtomicLong()).incrementAndGet();
        pathCounts.computeIfAbsent(path, _ -> new AtomicLong()).incrementAndGet();
        if (status >= 400) {
            pathErrorCounts.computeIfAbsent(path, _ -> new AtomicLong()).incrementAndGet();
        }

        // Micrometer 记录
        requestTimer.record(costMs, TimeUnit.MILLISECONDS);
        latencySummary.record(costMs);

        if (status >= 400) {
            Counter.builder("gateway.requests.error")
                    .description("Requests with status >= 400")
                    .tag("status", statusGroup)
                    .register(meterRegistry)
                    .increment();
        }

        Counter.builder("gateway.requests.total")
                .description("Total gateway requests")
                .tag("method", method)
                .tag("status", statusGroup)
                .register(meterRegistry)
                .increment();

        latencySamples.offer(costMs);
        while (latencySamples.size() > LATENCY_SAMPLES_MAX) {
            latencySamples.poll();
        }
        if (costMs >= SLOW_REQUEST_THRESHOLD_MS) {
            slowRequestCounter.increment();
            Map<String, Object> slow = new LinkedHashMap<>();
            slow.put("path", path);
            slow.put("method", method);
            slow.put("costMs", costMs);
            slow.put("status", status);
            slow.put("time", LocalDateTime.now().format(TIME_FMT));
            slowRequests.offer(slow);
            while (slowRequests.size() > SLOW_REQUEST_MAX) {
                slowRequests.poll();
            }
        }
    }

    /**
     * 记录一次异常（供最近异常日志展示）。
     *
     * @param message 异常摘要
     */
    public void recordError(String message) {
        recentErrors.offer(LocalDateTime.now().format(TIME_FMT) + " " + message);
        while (recentErrors.size() > RECENT_ERRORS_MAX) {
            recentErrors.poll();
        }
    }

    /**
     * 生成指标快照（与微服务版监控结构保持一致）。
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        long total = totalRequests.get();
        data.put("totalRequests", total);
        data.put("avgLatencyMs", total == 0 ? 0 : Math.round((double) totalLatencyMs.get() / total));
        data.put("latencyPercentiles", latencyPercentiles());
        data.put("statusCounts", sortedCounts(statusCounts));
        data.put("methodCounts", sortedCounts(methodCounts));
        data.put("topPaths", topPaths());
        data.put("pathErrors", topErrors());
        data.put("slowRequests", List.copyOf(slowRequests));
        data.put("recentErrors", List.copyOf(recentErrors));
        return data;
    }

    private Map<String, Object> latencyPercentiles() {
        List<Long> samples = new ArrayList<>(latencySamples);
        samples.sort(Comparator.naturalOrder());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("p50", percentile(samples, 50));
        result.put("p95", percentile(samples, 95));
        result.put("p99", percentile(samples, 99));
        return result;
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.clamp(idx, 0, sorted.size() - 1);
        return sorted.get(idx);
    }

    private Map<String, Object> sortedCounts(ConcurrentMap<String, AtomicLong> counts) {
        Map<String, Object> result = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, AtomicLong>>comparingLong(e -> e.getValue().get()).reversed())
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
        return result;
    }

    private Map<String, Object> topPaths() {
        Map<String, Object> result = new LinkedHashMap<>();
        pathCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, AtomicLong>>comparingLong(e -> e.getValue().get()).reversed())
                .limit(10)
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
        return result;
    }

    private Map<String, Object> topErrors() {
        Map<String, Object> result = new LinkedHashMap<>();
        pathErrorCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, AtomicLong>>comparingLong(e -> e.getValue().get()).reversed())
                .limit(10)
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
        return result;
    }
}
