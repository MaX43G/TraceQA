package edu.zjut.traceqa.service;

import edu.zjut.traceqa.model.vo.DocumentProgressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文档解析进度存储（内存版）。
 *
 * <p>使用 {@link ConcurrentHashMap} 保存每个文档的最新进度快照与切分后的各子块
 * LightRAG track_id 列表。后端不做轮询，由用户在管理端点击「刷新」时按需查询
 * LightRAG 状态并更新快照。</p>
 */
@Slf4j
@Component
public class DocumentProgressStore {

    private final ConcurrentMap<Long, DocumentProgressVO> snapshots = new ConcurrentHashMap<>();
    /** 每个文档的切分子块 track_id 列表（用于按需刷新时逐个查询状态） */
    private final ConcurrentMap<Long, List<String>> trackIds = new ConcurrentHashMap<>();

    /** 注册文档进度（初始化占位） */
    public void register(Long documentId, DocumentProgressVO initial) {
        snapshots.put(documentId, initial);
    }

    /** 更新文档进度快照 */
    public void update(DocumentProgressVO progress) {
        snapshots.put(progress.getDocumentId(), progress);
    }

    /** 获取最新进度快照，不存在返回 null */
    public DocumentProgressVO get(Long documentId) {
        return snapshots.get(documentId);
    }

    /** 保存文档切分后的各子块 track_id 列表 */
    public void putTrackIds(Long documentId, List<String> ids) {
        trackIds.put(documentId, ids);
    }

    /** 获取文档的切分子块 track_id 列表，不存在返回 null */
    public List<String> getTrackIds(Long documentId) {
        return trackIds.get(documentId);
    }

    /** 移除进度（文档删除时清理） */
    public void remove(Long documentId) {
        snapshots.remove(documentId);
        trackIds.remove(documentId);
    }
}