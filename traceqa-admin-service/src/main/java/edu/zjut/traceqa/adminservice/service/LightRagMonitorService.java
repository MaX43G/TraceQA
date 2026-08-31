package edu.zjut.traceqa.adminservice.service;

import edu.zjut.traceqa.common.config.LightRagClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * LightRAG 引擎监控服务（管理员）。
 *
 * <p>聚合 LightRAG Server 的只读运行时信息并提供运维操作，带 8 秒短路缓存。</p>
 */
@Service
public class LightRagMonitorService {

    private static final Logger log = LoggerFactory.getLogger(LightRagMonitorService.class);

    private static final long CACHE_TTL_MS = 8000L;
    private static final int POPULAR_LABELS_LIMIT = 50;

    private final LightRagClient lightRagClient;
    private volatile Map<String, Object> cached;
    private volatile long cacheExpireAt;

    public LightRagMonitorService(LightRagClient lightRagClient) {
        this.lightRagClient = lightRagClient;
    }

    /**
     * 只读信息面板快照（8 秒缓存）
     */
    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpireAt) {
            return cached;
        }
        Map<String, Object> data = Map.of(
                "pipeline", safeGet(lightRagClient::getPipelineStatus),
                "statusCounts", safeGet(lightRagClient::getStatusCounts),
                "models", safeGet(lightRagClient::getModels),
                "runningModels", safeGet(lightRagClient::getRunningModels),
                "popularLabels", safeGetList(() -> lightRagClient.getPopularLabels(POPULAR_LABELS_LIMIT))
        );
        cached = data;
        cacheExpireAt = now + CACHE_TTL_MS;
        return data;
    }

    public Map<String, Object> reprocessFailed() {
        return lightRagClient.reprocessFailed();
    }

    public Map<String, Object> clearCache() {
        return lightRagClient.clearCache();
    }

    public Map<String, Object> cancelPipeline() {
        return lightRagClient.cancelPipeline();
    }

    public Map<String, Object> scanDocuments() {
        return lightRagClient.scanDocuments();
    }

    private Map<String, Object> safeGet(Supplier<Map<String, Object>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("LightRAG 指标获取失败：{}", e.getMessage());
            return Map.of();
        }
    }

    private List<String> safeGetList(Supplier<List<String>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("LightRAG 列表获取失败：{}", e.getMessage());
            return List.of();
        }
    }
}