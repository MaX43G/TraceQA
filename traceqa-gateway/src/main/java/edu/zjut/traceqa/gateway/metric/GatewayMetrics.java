package edu.zjut.traceqa.gateway.metric;

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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关请求指标采集器。
 *
 * <p>作为系统统一入口，网关在此采集全部 HTTP 请求的运行指标（总请求数、延迟分位、
 * 状态/方法分布、Top 接口、慢请求、接口错误、最近异常日志），供管理服务聚合展示。
 * 指标为内存态，网关重启后清零。</p>
 */
@Component
public class GatewayMetrics {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final long SLOW_REQUEST_THRESHOLD_MS = 2000L;
    private static final int SLOW_REQUEST_MAX = 20;
    private static final int LATENCY_SAMPLES_MAX = 1000;
    private static final int RECENT_ERRORS_MAX = 50;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();

    private final ConcurrentMap<String, AtomicLong> statusCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> methodCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> pathCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> pathErrorCounts = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> slowRequests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> recentErrors = new ConcurrentLinkedQueue<>();

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

        statusCounts.computeIfAbsent((status / 100) + "xx", k -> new AtomicLong()).incrementAndGet();
        methodCounts.computeIfAbsent(method, k -> new AtomicLong()).incrementAndGet();
        pathCounts.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
        if (status >= 400) {
            pathErrorCounts.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
        }

        latencySamples.offer(costMs);
        while (latencySamples.size() > LATENCY_SAMPLES_MAX) {
            latencySamples.poll();
        }
        if (costMs >= SLOW_REQUEST_THRESHOLD_MS) {
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
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
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