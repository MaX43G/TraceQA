package edu.zjut.traceqa.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文档切分 track_id 存储（内存版）。
 *
 * <p>使用 {@link ConcurrentHashMap} 保存每个文档切分后的各子块 LightRAG track_id 列表，
 * 供按需刷新时逐个查询状态。后端不做轮询，由用户在管理端点击「刷新」时按需查询
 * LightRAG 状态并更新数据库。</p>
 */
@Component
public class DocumentProgressStore {

    /**
     * 每个文档的切分子块 track_id 列表（用于按需刷新时逐个查询状态）
     */
    private final ConcurrentMap<Long, List<String>> trackIds = new ConcurrentHashMap<>();

    /**
     * 保存文档切分后的各子块 track_id 列表
     */
    public void putTrackIds(Long documentId, List<String> ids) {
        trackIds.put(documentId, ids);
    }

    /**
     * 获取文档的切分子块 track_id 列表，不存在返回 null
     */
    public List<String> getTrackIds(Long documentId) {
        return trackIds.get(documentId);
    }

    /**
     * 移除 track_id（文档删除时清理）
     */
    public void remove(Long documentId) {
        trackIds.remove(documentId);
    }
}