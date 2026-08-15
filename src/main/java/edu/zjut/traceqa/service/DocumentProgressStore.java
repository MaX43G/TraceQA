package edu.zjut.traceqa.service;

import edu.zjut.traceqa.dto.document.DocumentProgressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文档解析进度存储（内存版）。
 *
 * <p>使用 {@link ConcurrentHashMap} 保存每个文档的最新进度快照，
 * 供进度 SSE 断线重连时立即补发。初期不引入 Redis，满足极简架构要求。</p>
 */
@Slf4j
@Component
public class DocumentProgressStore {

    private final ConcurrentMap<Long, DocumentProgressVO> snapshots = new ConcurrentHashMap<>();

    /** 注册文档进度（初始化占位） */
    public void register(Long documentId, DocumentProgressVO initial) {
        snapshots.put(documentId, initial);
    }

    /** 更新文档进度快照 */
    public void update(DocumentProgressVO progress) {
        snapshots.put(progress.documentId(), progress);
    }

    /** 获取最新进度快照，不存在返回 null */
    public DocumentProgressVO get(Long documentId) {
        return snapshots.get(documentId);
    }

    /** 移除进度（文档删除时清理） */
    public void remove(Long documentId) {
        snapshots.remove(documentId);
    }
}