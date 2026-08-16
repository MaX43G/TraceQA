package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.dto.document.DocumentVO;
import edu.zjut.traceqa.entity.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 文档解析服务（独立 Bean，确保 {@code @Async} 代理生效）。
 *
 * <p>由 {@link DocumentService#upload} 跨 Bean 调用，在 {@code docExecutor} 线程池中
 * 异步执行 LightRAG 文档解析与进度追踪，上传接口立即返回 202 不阻塞。</p>
 */
@Slf4j
@Component
public class DocumentParseWorker {

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private LightRagClient lightRagClient;

    @Resource
    private DocumentProgressStore progressStore;

    /** 解析轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 2000L;
    /** 解析轮询最大次数（约 10 分钟） */
    private static final int MAX_POLL_TIMES = 300;

    

    /** 异步执行 LightRAG 解析（线程池执行，上传后立即返回） */
    @Async("docExecutor")
    public void parse(Document doc, byte[] content, String filename) {
        try {
            updateStatus(doc, DocumentStatus.PROCESSING, 15, "正在提交至知识图谱引擎");
            // 1. 提交 LightRAG 上传（内容已在请求线程中读出，避免临时文件被清理）
            String trackId = lightRagClient.uploadDocument(content, filename);
            doc.setTrackId(trackId);
            documentMapper.updateById(doc);
            updateStatus(doc, DocumentStatus.PROCESSING, 30, "正在抽取文本与知识图谱");

            // 2. 轮询解析状态
            parseAndWait(doc);
        } catch (BizException e) {
            failDocument(doc, e.getMessage());
        } catch (Exception e) {
            log.error("文档解析异常：{}", doc.getOriginalName(), e);
            failDocument(doc, "解析失败，请稍后重试");
        }
    }

    /** 轮询 LightRAG 解析状态直至完成 */
    private void parseAndWait(Document doc) {
        int poll = 0;
        while (poll < MAX_POLL_TIMES) {
            Map<String, Object> status = lightRagClient.queryTrackStatus(doc.getTrackId());
            String state = resolveDocState(status);
            if (isDoneState(state)) {
                applyStats(doc, status);
                updateStatus(doc, DocumentStatus.DONE, 100, "解析完成");
                return;
            }
            if (isFailedState(state)) {
                throw new BizException(ErrorCode.FILE_ERROR, "LightRAG 解析失败");
            }
            poll++;
            updateStatus(doc, DocumentStatus.PROCESSING, Math.min(90, 30 + poll), "正在构建知识图谱");
            sleepQuietly(POLL_INTERVAL_MS);
        }
        throw new BizException(ErrorCode.FILE_ERROR, "解析超时，请稍后重试");
    }

    /** 从 LightRAG 状态响应中解析文档状态（取 documents 数组首个元素） */
    private String resolveDocState(Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.get(0);
            if (first instanceof Map<?, ?> docMap && docMap.get("status") != null) {
                return String.valueOf(docMap.get("status"));
            }
        }
        Object state = status.get("status");
        return state == null ? "processing" : String.valueOf(state);
    }

    /** 判断是否为完成状态 */
    private boolean isDoneState(String state) {
        return "processed".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state)
                || "success".equalsIgnoreCase(state);
    }

    /** 判断是否为失败状态 */
    private boolean isFailedState(String state) {
        return "failed".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state);
    }

    /** 从 LightRAG 状态结果中提取统计数据 */
    private void applyStats(Document doc, Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.get(0);
            if (first instanceof Map<?, ?> docMap) {
                doc.setChunkCount(intOf(docMap.get("chunks_count")));
            }
        }
    }

    /** 更新文档状态与进度 */
    private void updateStatus(Document doc, DocumentStatus status, int progress, String message) {
        doc.setStatus(status.name());
        documentMapper.updateById(doc);
        progressStore.update(new edu.zjut.traceqa.dto.document.DocumentProgressVO(
                doc.getId(), doc.getTrackId(), doc.getStatus(), progress,
                doc.getChunkCount(), doc.getEntityCount(), doc.getRelationCount(), message));
    }

    /** 标记解析失败 */
    private void failDocument(Document doc, String message) {
        doc.setStatus(DocumentStatus.FAILED.name());
        doc.setErrorMsg(message);
        documentMapper.updateById(doc);
        progressStore.update(new edu.zjut.traceqa.dto.document.DocumentProgressVO(
                doc.getId(), doc.getTrackId(), doc.getStatus(), 100,
                doc.getChunkCount(), doc.getEntityCount(), doc.getRelationCount(), message));
        log.warn("文档解析失败：{}，原因：{}", doc.getOriginalName(), message);
    }

    /** 安全获取整数 */
    private int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /** 静默睡眠 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}