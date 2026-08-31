package edu.zjut.traceqa.kbservice.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文档解析进度存储（内存态）。
 *
 * <p>记录文档各分片提交到 LightRAG 后返回的 track_id 列表，供按需刷新解析状态使用。
 * 知识库服务为单实例部署，内存态即可满足；若需水平扩容应迁移到 Redis/DB。</p>
 */
@Component
public class DocumentProgressStore {

    private final ConcurrentMap<Long, List<String>> trackIds = new ConcurrentHashMap<>();

    /**
     * 记录文档的 track_id 列表
     */
    public void putTrackIds(Long documentId, List<String> ids) {
        trackIds.put(documentId, ids);
    }

    /**
     * 读取文档的 track_id 列表
     */
    public List<String> getTrackIds(Long documentId) {
        return trackIds.get(documentId);
    }

    /**
     * 移除文档的 track_id 记录
     */
    public void remove(Long documentId) {
        trackIds.remove(documentId);
    }
}