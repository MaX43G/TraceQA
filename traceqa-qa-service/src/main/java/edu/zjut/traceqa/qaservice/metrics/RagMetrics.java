package edu.zjut.traceqa.qaservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 链路专用 Micrometer 指标采集器。
 *
 * <p>通过 /actuator/prometheus 暴露以下指标：</p>
 * <ul>
 *   <li>{@code rag.query.latency} — 完整 RAG 问答耗时（按 intent 标签）</li>
 *   <li>{@code rag.stage.latency} — 各 Agent 阶段耗时（按 stage 标签）</li>
 *   <li>{@code rag.retrieval.hits} — 检索命中数分布（按 path 标签：graph/vector/keyword）</li>
 *   <li>{@code rag.intent.distribution} — 意图分类计数（按 intent 标签）</li>
 *   <li>{@code rag.answer.degraded} — 降级回答计数</li>
 *   <li>{@code rag.cache.hit} — 缓存命中计数</li>
 *   <li>{@code rag.llm.call} — LLM 调用计数（按 model+status 标签）</li>
 *   <li>{@code rag.concurrent.queries} — 当前并发查询数</li>
 * </ul>
 */
@Component
public class RagMetrics {

    @Resource
    private MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Timer> stageTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> intentTimers = new ConcurrentHashMap<>();
    private Counter degradedCounter;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private final AtomicInteger concurrentQueries = new AtomicInteger();

    @PostConstruct
    void init() {
        degradedCounter = Counter.builder("rag.answer.degraded")
                .description("Number of degraded (non-AI) answers")
                .register(meterRegistry);
        cacheHitCounter = Counter.builder("rag.cache.hit")
                .description("Number of intent cache hits")
                .register(meterRegistry);
        cacheMissCounter = Counter.builder("rag.cache.miss")
                .description("Number of intent cache misses")
                .register(meterRegistry);
        meterRegistry.gauge("rag.concurrent.queries", concurrentQueries, AtomicInteger::get);
    }

    /** 记录完整 RAG 问答耗时 */
    public void recordQueryLatency(long latencyMs, String intent) {
        intentTimers.computeIfAbsent(intent, _ ->
                Timer.builder("rag.query.latency")
                        .description("End-to-end RAG query latency")
                        .tag("intent", intent)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        ).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    /** 记录单个 Agent 阶段耗时 */
    public void recordStageLatency(String stage, long latencyMs) {
        stageTimers.computeIfAbsent(stage, _ ->
                Timer.builder("rag.stage.latency")
                        .description("Per-stage RAG pipeline latency")
                        .tag("stage", stage)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        ).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    /** 记录检索命中数 */
    public void recordRetrievalHits(String path, int hits) {
        DistributionSummary.builder("rag.retrieval.hits")
                .description("Number of retrieval hits per path")
                .tag("path", path)
                .baseUnit("hits")
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry)
                .record(hits);
    }

    /** 记录意图分类 */
    public void recordIntent(String intent) {
        Counter.builder("rag.intent.distribution")
                .description("Intent classification distribution")
                .tag("intent", intent)
                .register(meterRegistry)
                .increment();
    }

    /** 记录降级回答 */
    public void recordDegraded() {
        degradedCounter.increment();
    }

    /** 记录缓存命中 */
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    /** 记录缓存未命中 */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    /** 记录 LLM 调用 */
    public void recordLlmCall(String model, boolean success) {
        Counter.builder("rag.llm.call")
                .description("LLM API call count")
                .tag("model", model)
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    /** 进入查询（增加并发计数） */
    public void queryStart() {
        concurrentQueries.incrementAndGet();
    }

    /** 查询结束（减少并发计数） */
    public void queryEnd() {
        concurrentQueries.decrementAndGet();
    }
}
