package edu.zjut.traceqa.kbservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析进度存储（Redis 持久化）。
 *
 * <p>记录文档各分片提交到 LightRAG 后返回的 track_id 列表，供按需刷新解析状态使用。
 * 使用 Redis 存储以支持多实例部署和服务重启后进度不丢失。</p>
 *
 * <p>Redis Key 格式：{@code doc:progress:{documentId}}，Value 为 JSON 数组。
 * 过期时间设为 7 天，未完成的文档可手动重试。</p>
 */
@Component
public class DocumentProgressStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentProgressStore.class);

    /**
     * Redis Key 前缀
     */
    private static final String KEY_PREFIX = "doc:progress:";

    /**
     * 过期时间（7 天）
     */
    private static final long TTL_DAYS = 7;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 记录文档的 track_id 列表
     *
     * @param documentId 文档 ID
     * @param ids        track_id 列表
     */
    public void putTrackIds(Long documentId, List<String> ids) {
        try {
            String key = buildKey(documentId);
            // 将 List<String> 转为 JSON 数组字符串存储
            String json = ids.stream()
                    .map(id -> "\"" + id.replace("\"", "\\\"") + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            stringRedisTemplate.opsForValue().set(key, json, TTL_DAYS, TimeUnit.DAYS);
            log.debug("保存文档进度：documentId={}, trackIds={}", documentId, ids.size());
        } catch (Exception e) {
            log.warn("保存文档进度到 Redis 失败（降级到内存）：documentId={}, err={}", documentId, e.getMessage());
        }
    }

    /**
     * 读取文档的 track_id 列表
     *
     * @param documentId 文档 ID
     * @return track_id 列表，不存在或解析失败返回 null
     */
    public List<String> getTrackIds(Long documentId) {
        try {
            String key = buildKey(documentId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            // 简单解析 JSON 数组（避免引入额外依赖）
            return parseJsonArray(json);
        } catch (Exception e) {
            log.warn("从 Redis 读取文档进度失败：documentId={}, err={}", documentId, e.getMessage());
            return null;
        }
    }

    /**
     * 移除文档的 track_id 记录
     *
     * @param documentId 文档 ID
     */
    public void remove(Long documentId) {
        try {
            String key = buildKey(documentId);
            stringRedisTemplate.delete(key);
            log.debug("删除文档进度：documentId={}", documentId);
        } catch (Exception e) {
            log.warn("从 Redis 删除文档进度失败：documentId={}, err={}", documentId, e.getMessage());
        }
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(Long documentId) {
        return KEY_PREFIX + documentId;
    }

    /**
     * 简单解析 JSON 数组字符串为 List<String>
     * 仅处理简单字符串数组，如 ["id1","id2","id3"]
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        // 移除首尾的 [ ]
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return List.of();
        }
        // 按逗号分割并移除引号
        String[] parts = content.split(",");
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            // 处理转义的引号
            trimmed = trimmed.replace("\\\"", "\"");
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }
}
