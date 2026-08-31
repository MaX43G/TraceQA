package edu.zjut.traceqa.kbservice.service;

import edu.zjut.traceqa.common.config.LightRagClient;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.Document;
import edu.zjut.traceqa.common.model.vo.DocumentVO;
import edu.zjut.traceqa.kbservice.mapper.DocumentMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档解析 Worker。
 *
 * <p>将文档切块后逐块提交到 LightRAG 入库；解析进度由用户触发刷新时查询 LightRAG。</p>
 */
@Service
public class DocumentParseWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseWorker.class);

    /**
     * PDF 切块阈值（>2MB 才切）
     */
    private static final long SPLIT_THRESHOLD_BYTES = 2L * 1024 * 1024;
    /**
     * PDF 目标单块大小
     */
    private static final long TARGET_PART_BYTES = 4L * 1024 * 1024;
    /**
     * 文本切块阈值（>1MB 才切）
     */
    private static final long TEXT_SPLIT_THRESHOLD_BYTES = 1024L * 1024;
    /**
     * 文本目标单块大小
     */
    private static final long TEXT_PART_BYTES = 1024L * 1024;
    /**
     * 文本最大切块数
     */
    private static final int MAX_TEXT_PARTS = 8;
    /**
     * 全局最大切块数
     */
    private static final int MAX_PARTS = 50;
    /**
     * 块间提交限速（毫秒）
     */
    private static final long PART_INTERVAL_MS = 1000L;

    private final DocumentMapper documentMapper;
    private final LightRagClient lightRagClient;
    private final DocumentProgressStore progressStore;

    public DocumentParseWorker(DocumentMapper documentMapper, LightRagClient lightRagClient,
                               DocumentProgressStore progressStore) {
        this.documentMapper = documentMapper;
        this.lightRagClient = lightRagClient;
        this.progressStore = progressStore;
    }

    @Data
    @AllArgsConstructor
    private static class PartChunk {
        private byte[] bytes;
        private String name;
    }

    /**
     * 提交文档解析（切块并逐块上传 LightRAG）
     */
    public boolean submit(Document doc, byte[] content, String filename) {
        try {
            List<PartChunk> parts = splitParts(content, filename);
            int total = parts.size();
            doc.setPartTotal(total);
            doc.setPartDone(0);
            doc.setStatus(DocumentStatus.PROCESSING.name());
            documentMapper.updateById(doc);
            log.info("文档开始提交：{}，切分为 {} 块", doc.getOriginalName(), total);

            List<String> trackIds = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                PartChunk part = parts.get(i);
                String partName = total > 1 ? safePartName(part.getName(), i + 1, total) : part.getName();
                String trackId = lightRagClient.uploadDocument(part.getBytes(), partName);
                trackIds.add(trackId);
                doc.setTrackId(trackId);
                doc.setPartDone(i + 1);
                documentMapper.updateById(doc);
                if (i < total - 1) {
                    sleepQuietly();
                }
            }
            progressStore.putTrackIds(doc.getId(), trackIds);
            return true;
        } catch (BizException e) {
            failDocument(doc, e.getMessage());
            cleanupFailedLightRag(doc);
            return false;
        } catch (Exception e) {
            log.error("文档提交异常：{}", doc.getOriginalName(), e);
            failDocument(doc, "提交失败，请稍后重试");
            cleanupFailedLightRag(doc);
            return false;
        }
    }

    /**
     * 按需刷新解析状态（聚合各 track 的完成度与抽取统计）
     */
    public DocumentVO refresh(Document doc) {
        List<String> trackIds = progressStore.getTrackIds(doc.getId());
        if ((trackIds == null || trackIds.isEmpty())
                && doc.getTrackId() != null && !doc.getTrackId().isBlank()) {
            trackIds = List.of(doc.getTrackId());
        }
        if (trackIds == null || trackIds.isEmpty()) {
            return DocumentVO.of(doc);
        }

        int total = trackIds.size();
        int done = 0;
        int failed = 0;
        int chunk = 0;
        int entity = 0;
        int relation = 0;
        for (String trackId : trackIds) {
            try {
                Map<String, Object> status = lightRagClient.queryTrackStatus(trackId);
                String state = resolveDocState(status);
                if (isDoneState(state)) {
                    done++;
                    Map<?, ?> stats = firstDocMap(status);
                    chunk += intOf(stats == null ? null : stats.get("chunks_count"));
                    entity += intOf(stats == null ? null : stats.get("entities_count"));
                    relation += intOf(stats == null ? null : stats.get("relations_count"));
                } else if (isFailedState(state)) {
                    failed++;
                }
            } catch (Exception e) {
                log.debug("刷新查询异常（视为处理中）：trackId={}, err={}", trackId, e.getMessage());
            }
        }

        if (failed > 0) {
            doc.setStatus(DocumentStatus.FAILED.name());
            doc.setErrorMsg("LightRAG 解析失败（" + failed + "/" + total + " 块）");
        } else if (done == total) {
            doc.setStatus(DocumentStatus.DONE.name());
            doc.setErrorMsg(null);
        } else {
            doc.setStatus(DocumentStatus.PROCESSING.name());
            doc.setErrorMsg(null);
        }
        doc.setPartDone(done);
        doc.setChunkCount(chunk);
        doc.setEntityCount(entity);
        doc.setRelationCount(relation);
        documentMapper.updateById(doc);
        log.info("文档状态刷新：{}，status={}，done={}/{}", doc.getOriginalName(), doc.getStatus(), done, total);
        return DocumentVO.of(doc);
    }

    /**
     * 清理 LightRAG 中已提交的失败记录（避免去重冲突）
     */
    private void cleanupFailedLightRag(Document doc) {
        if (doc.getTrackId() == null || doc.getTrackId().isBlank()) {
            return;
        }
        try {
            Map<String, Object> status = lightRagClient.queryTrackStatus(doc.getTrackId());
            Object docs = status.get("documents");
            if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
                Object first = documentList.getFirst();
                if (first instanceof Map<?, ?> docMap && docMap.get("id") != null) {
                    lightRagClient.deleteDocument(String.valueOf(docMap.get("id")));
                }
            }
        } catch (Exception e) {
            log.debug("清理 LightRAG 失败记录异常：{}", e.getMessage());
        }
    }

    private List<PartChunk> splitParts(byte[] content, String filename) throws IOException {
        String ext = extensionOf(filename);
        if (("md".equals(ext) || "txt".equals(ext)) && content.length > TEXT_SPLIT_THRESHOLD_BYTES) {
            return splitText(content, filename);
        }
        if ("pdf".equals(ext) && content.length > SPLIT_THRESHOLD_BYTES) {
            return splitPdf(content, filename);
        }
        return List.of(new PartChunk(content, filename));
    }

    private List<PartChunk> splitPdf(byte[] content, String filename) throws IOException {
        List<PartChunk> parts = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(content)) {
            int totalPages = pdf.getNumberOfPages();
            if (totalPages <= 1) {
                return List.of(new PartChunk(content, filename));
            }
            int pagesPerPart = estimatePagesPerPart(content.length, totalPages);
            for (int start = 0; start < totalPages; start += pagesPerPart) {
                int end = Math.min(start + pagesPerPart, totalPages);
                try (PDDocument partPdf = new PDDocument()) {
                    for (int p = start; p < end; p++) {
                        partPdf.addPage(pdf.getPage(p));
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    partPdf.save(out);
                    parts.add(new PartChunk(out.toByteArray(), filename));
                }
            }
        }
        return parts;
    }

    private int estimatePagesPerPart(long bytes, int totalPages) {
        long pagesPerPart = Math.max(1L, Math.round((double) totalPages * TARGET_PART_BYTES / Math.max(1L, bytes)));
        long parts = (long) Math.ceil(totalPages / (double) pagesPerPart);
        if (parts > MAX_PARTS) {
            pagesPerPart = Math.max(1L, (long) Math.ceil(totalPages / (double) MAX_PARTS));
        }
        return (int) pagesPerPart;
    }

    private List<PartChunk> splitText(byte[] content, String filename) {
        if (content.length == 0) {
            return List.of(new PartChunk(content, filename));
        }
        String text = new String(content, StandardCharsets.UTF_8);
        int targetChars = Math.max(1, (int) (TEXT_PART_BYTES / 3));
        int chunks = Math.max(1, (int) Math.ceil(text.length() / (double) targetChars));
        int capped = Math.min(chunks, MAX_TEXT_PARTS);
        int partChars = Math.max(1, (int) Math.ceil(text.length() / (double) capped));
        List<PartChunk> parts = new ArrayList<>();
        for (int start = 0; start < text.length(); start += partChars) {
            int end = Math.min(start + partChars, text.length());
            parts.add(new PartChunk(text.substring(start, end).getBytes(StandardCharsets.UTF_8), filename));
        }
        return parts;
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String safePartName(String filename, int index, int total) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : (filename == null ? "file" : filename);
        String ext = dot > 0 ? filename.substring(dot) : "";
        return base + "_part" + index + "of" + total + ext;
    }

    private String resolveDocState(Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.getFirst();
            if (first instanceof Map<?, ?> docMap && docMap.get("status") != null) {
                return String.valueOf(docMap.get("status"));
            }
        }
        Object state = status.get("status");
        return state == null ? "processing" : String.valueOf(state);
    }

    private Map<?, ?> firstDocMap(Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.getFirst();
            if (first instanceof Map<?, ?> docMap) {
                return docMap;
            }
        }
        return null;
    }

    private boolean isDoneState(String state) {
        return "processed".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state)
                || "success".equalsIgnoreCase(state);
    }

    private boolean isFailedState(String state) {
        return "failed".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state);
    }

    private void failDocument(Document doc, String message) {
        doc.setStatus(DocumentStatus.FAILED.name());
        doc.setErrorMsg(message);
        documentMapper.updateById(doc);
        log.warn("文档解析失败：{}，原因：{}", doc.getOriginalName(), message);
    }

    private int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(PART_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}