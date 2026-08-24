package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.model.vo.DocumentProgressVO;
import edu.zjut.traceqa.model.vo.DocumentVO;
import edu.zjut.traceqa.model.po.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务。
 *
 * <p>由 {@link DocumentQueueWorker}（Redis Stream 队列消费者）同步调用。文档提交
 * 阶段仅做「切块 + 限速上传 LightRAG」，**不进行后端轮询**；LightRAG 的抽取状态
 * 由用户在管理端点击「刷新」时通过 {@link #refresh(Document)} 按需查询一次并聚合
 * 更新，避免后台持续轮询占用系统资源。</p>
 *
 * <p>超大文件（PDF / 文本）自动切分为多个子块，逐块以限速间隔提交 LightRAG，
 * 每块独立 track_id，进度按「已完成块数 / 总块数」聚合展示，
 * 单块失败只影响该块，避免整篇重来与瞬时打爆限流。</p>
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

    /** 切分阈值：PDF 超过该字节数才切分 */
    private static final long SPLIT_THRESHOLD_BYTES = 2L * 1024 * 1024;
    /** PDF 每块目标字节数（约 4MB） */
    private static final long TARGET_PART_BYTES = 4L * 1024 * 1024;
    /**
     * 文本（md/txt）切分阈值：低于该字节数的文档不切分、整体提交为一个 LightRAG 文档。
     *
     * <p>LightRAG 抽取实体/关系时每个 chunk 都要调用一次 LLM，按 100KB 切块会成倍放大
     * LLM 调用次数（一个 700KB 文档被切成 7 块 → 7 个独立文档 → 每块内部再切 ~19 chunk，
     * 总计百余次 LLM 抽取），这是「小文档上传极慢」的根因。仅对真正的大文档切块。</p>
     */
    private static final long TEXT_SPLIT_THRESHOLD_BYTES = 1024L * 1024;
    /** 文本（md/txt）每块目标字节数（大文档切块时使用） */
    private static final long TEXT_PART_BYTES = 1024L * 1024;
    /** 文本切分最大块数 */
    private static final int MAX_TEXT_PARTS = 8;
    /** 最大切分块数（防止切得过碎） */
    private static final int MAX_PARTS = 50;
    /** 块与块之间的提交限速间隔（毫秒），仅在大文档多块时生效，避免不必要等待 */
    private static final long PART_INTERVAL_MS = 1000L;

    /** 单个子块：内容 + 文件名 */
    private record PartChunk(byte[] bytes, String name) {
    }

    /**
     * 提交文档至 LightRAG（由队列消费者调用，仅上传不轮询），返回是否成功。
     *
     * <p>超大文件自动切块逐块限速提交，保存每个子块的 track_id；之后不再后台轮询，
     * 由用户在前端点击「刷新」时按需查询。失败时清理 LightRAG 中残留的失败记录
     * （供队列重试时重新上传，避免内容去重拦截）。</p>
     */
    public boolean submit(Document doc, byte[] content, String filename) {
        try {
            List<PartChunk> parts = splitParts(content, filename);
            int total = parts.size();
            doc.setPartTotal(total);
            doc.setPartDone(0);
            documentMapper.updateById(doc);
            log.info("文档开始提交：{}，切分为 {} 块", doc.getOriginalName(), total);

            List<String> trackIds = new ArrayList<>();
            // 逐块限速提交（仅上传，不轮询）
            for (int i = 0; i < total; i++) {
                PartChunk part = parts.get(i);
                String partName = total > 1 ? safePartName(part.name(), i + 1, total) : part.name();
                String trackId = lightRagClient.uploadDocument(part.bytes(), partName);
                trackIds.add(trackId);
                doc.setTrackId(trackId);
                doc.setPartDone(i + 1);
                documentMapper.updateById(doc);
                updateStatus(doc, DocumentStatus.PROCESSING, uploadProgress(total, i + 1),
                        "已上传第 " + (i + 1) + "/" + total + " 块");
                if (i < total - 1) {
                    sleepQuietly(PART_INTERVAL_MS);
                }
            }
            progressStore.putTrackIds(doc.getId(), trackIds);
            updateStatus(doc, DocumentStatus.PROCESSING, uploadProgress(total, total),
                    "全部 " + total + " 块已提交，等待 LightRAG 抽取（可点击刷新查看进度）");
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
     * 按需刷新文档解析状态（由前端「刷新」按钮触发）。
     *
     * <p>逐个查询该文档各子块的 LightRAG track_status 一次，聚合后更新数据库与进度
     * 快照并返回最新 {@link DocumentVO}。任意块失败即整篇失败；全部完成即 DONE；
     * 否则保持 PROCESSING。</p>
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

        String message;
        if (failed > 0) {
            doc.setStatus(DocumentStatus.FAILED.name());
            doc.setErrorMsg("LightRAG 解析失败（" + failed + "/" + total + " 块）");
            message = doc.getErrorMsg();
        } else if (done == total) {
            doc.setStatus(DocumentStatus.DONE.name());
            doc.setErrorMsg(null);
            message = "解析完成";
        } else {
            doc.setStatus(DocumentStatus.PROCESSING.name());
            message = "图谱构建中（向量检索已可用），已完成 " + done + "/" + total + " 块";
        }
        doc.setPartDone(done);
        doc.setChunkCount(chunk);
        doc.setEntityCount(entity);
        doc.setRelationCount(relation);
        documentMapper.updateById(doc);
        progressStore.update(new DocumentProgressVO(
                doc.getId(), doc.getTrackId(), doc.getStatus(),
                (int) Math.min(100, done * 100.0 / Math.max(1, total)),
                doc.getPartTotal(), doc.getPartDone(),
                chunk, entity, relation, message));
        log.info("文档状态刷新：{}，status={}，done={}/{}", doc.getOriginalName(), doc.getStatus(), done, total);
        return DocumentVO.of(doc);
    }

    /** 清理 LightRAG 中该文档的失败记录（重试前删除，避免内容去重拦截） */
    private void cleanupFailedLightRag(Document doc) {
        if (doc.getTrackId() == null || doc.getTrackId().isBlank()) {
            return;
        }
        try {
            Map<String, Object> status = lightRagClient.queryTrackStatus(doc.getTrackId());
            Object docs = status.get("documents");
            if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
                Object first = documentList.get(0);
                if (first instanceof Map<?, ?> docMap && docMap.get("id") != null) {
                    lightRagClient.deleteDocument(String.valueOf(docMap.get("id")));
                }
            }
        } catch (Exception e) {
            log.debug("清理 LightRAG 失败记录异常：{}", e.getMessage());
        }
    }

    /** 计算上传进度（已提交成功的块数占比，0-100） */
    private int uploadProgress(int total, int uploaded) {
        return (int) Math.min(100, uploaded * 100.0 / Math.max(1, total));
    }

    /** 切分文件为多个子块；md/txt 仅超大时切分，PDF 仅超大时切分 */
    private List<PartChunk> splitParts(byte[] content, String filename) throws IOException {
        String ext = extensionOf(filename);
        if (("md".equals(ext) || "txt".equals(ext)) && content.length > TEXT_SPLIT_THRESHOLD_BYTES) {
            return splitText(content, filename);
        }
        if ("pdf".equals(ext) && content.length > SPLIT_THRESHOLD_BYTES) {
            return splitPdf(content, filename);
        }
        // 小文件（含绝大多数 md/txt）整文件提交为单个 LightRAG 文档，避免放大 LLM 抽取成本
        return List.of(new PartChunk(content, filename));
    }

    /** 按页切分 PDF，每块约 TARGET_PART_BYTES */
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

    /** 估算每块页数：按字节比例 + 限制总块数 */
    private int estimatePagesPerPart(long bytes, int totalPages) {
        long pagesPerPart = Math.max(1L, Math.round((double) totalPages * TARGET_PART_BYTES / Math.max(1L, bytes)));
        long parts = (long) Math.ceil(totalPages / (double) pagesPerPart);
        if (parts > MAX_PARTS) {
            pagesPerPart = Math.max(1L, (long) Math.ceil(totalPages / (double) MAX_PARTS));
        }
        return (int) pagesPerPart;
    }

    /** 按字符切分纯文本（md/txt），确保每块都是合法 UTF-8（避免切断多字节字符） */
    private List<PartChunk> splitText(byte[] content, String filename) {
        if (content.length == 0) {
            return List.of(new PartChunk(content, filename));
        }
        String text = new String(content, StandardCharsets.UTF_8);
        // 中文字符最多 3 字节，按 TEXT_PART_BYTES 折算成字符目标，保证每块不超过目标字节数
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

    /** 提取文件扩展名（小写，不含点） */
    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /** 生成安全的子块文件名（不含空格/方括号，避免触发 LightRAG 安全校验） */
    private String safePartName(String filename, int index, int total) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : (filename == null ? "file" : filename);
        String ext = dot > 0 ? filename.substring(dot) : "";
        return base + "_part" + index + "of" + total + ext;
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

    /** 取 LightRAG 状态响应 documents 数组首个元素，无则返回 null */
    private Map<?, ?> firstDocMap(Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.get(0);
            if (first instanceof Map<?, ?> docMap) {
                return docMap;
            }
        }
        return null;
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

    /** 更新文档状态与进度 */
    private void updateStatus(Document doc, DocumentStatus status, int progress, String message) {
        doc.setStatus(status.name());
        documentMapper.updateById(doc);
        progressStore.update(new DocumentProgressVO(
                doc.getId(), doc.getTrackId(), doc.getStatus(), progress,
                doc.getPartTotal(), doc.getPartDone(),
                doc.getChunkCount(), doc.getEntityCount(), doc.getRelationCount(), message));
    }

    /** 标记解析失败 */
    private void failDocument(Document doc, String message) {
        doc.setStatus(DocumentStatus.FAILED.name());
        doc.setErrorMsg(message);
        documentMapper.updateById(doc);
        progressStore.update(new DocumentProgressVO(
                doc.getId(), doc.getTrackId(), doc.getStatus(), 100,
                doc.getPartTotal(), doc.getPartDone(),
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
