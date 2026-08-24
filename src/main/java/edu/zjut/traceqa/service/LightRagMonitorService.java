package edu.zjut.traceqa.service;

import edu.zjut.traceqa.config.LightRagClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * LightRAG 引擎监控服务（管理员）。
 *
 * <p>聚合 LightRAG 官方 Server 暴露的只读运行信息（流水线状态、文档状态分布、
 * 模型信息、图谱热门标签），供管理后台可视化；并提供重试失败/清缓存/取消流水线/
 * 触发扫描等运维操作。只读面板带短缓存，避免前端轮询频繁打到 LightRAG。</p>
 */
@Slf4j
@Service
public class LightRagMonitorService {

    /** 只读面板数据缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 8000L;
    /** 图谱热门标签数量 */
    private static final int POPULAR_LABELS_LIMIT = 50;

    @Resource
    private LightRagClient lightRagClient;

    private volatile Map<String, Object> cached;
    private volatile long cacheExpireAt = 0L;

    /** 组装 LightRAG 只读信息面板（带短缓存） */
    public Map<String, Object> snapshot() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpireAt) {
            return cached;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pipeline", safeGet(lightRagClient::getPipelineStatus));
        data.put("statusCounts", safeGet(lightRagClient::getStatusCounts));
        data.put("models", safeGet(lightRagClient::getModels));
        data.put("runningModels", safeGet(lightRagClient::getRunningModels));
        data.put("popularLabels", safeGet(() -> lightRagClient.getPopularLabels(POPULAR_LABELS_LIMIT)));
        cached = data;
        cacheExpireAt = now + CACHE_TTL_MS;
        return data;
    }

    /** 重试 LightRAG 中解析失败的文档 */
    public Map<String, Object> reprocessFailed() {
        return lightRagClient.reprocessFailed();
    }

    /** 清空 LightRAG 缓存 */
    public Map<String, Object> clearCache() {
        return lightRagClient.clearCache();
    }

    /** 取消当前运行的索引流水线 */
    public Map<String, Object> cancelPipeline() {
        return lightRagClient.cancelPipeline();
    }

    /** 触发 LightRAG 目录扫描 */
    public Map<String, Object> scanDocuments() {
        return lightRagClient.scanDocuments();
    }

    /** 单点查询失败时降级为空，不拖垮整个面板 */
    private Map<String, Object> safeGet(Supplier<Map<String, Object>> supplier) {
        try {
            Map<String, Object> result = supplier.get();
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            log.debug("LightRAG 面板单项查询失败：{}", e.getMessage());
            return Map.of();
        }
    }
}