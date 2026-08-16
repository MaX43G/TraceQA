package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.dto.document.DocumentProgressVO;
import edu.zjut.traceqa.entity.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务（独立 Bean，确保 {@code @Async} 代理生效）。
 *
 * <p>由 {@link DocumentService#upload} 跨 Bean 调用，在 {@code docExecutor} 线程池中
 * 异步执行 LightRAG 文档解析与进度追踪，上传接口立即返回 202 不阻塞。</p>
 *
 * <p>超大文件（PDF / 文本）自动切分为多个子块，逐块以限速间隔提交 LightRAG，
 * 每块独立 track_id 轮询，进度按「已完成块数 / 总块数」聚合展示，
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

    /** 解析轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 2000L;
    /** 解析轮询最大次数（约 10 分钟/块） */
    private static final int MAX_POLL_TIMES = 300;
    /** 切分阈值：PDF 超过该字节数才切分 */
    private static final long SPLIT_THRESHOLD_BYTES = 2L * 1024 * 1024;
    /** PDF 每块目标字节数（约 4MB） */
    private static final long TARGET_PART_BYTES = 4L * 1024 * 1024;
    /** 文本（md/txt）每块目标字节数（无论大小均切分，便于展示上传进度） */
    private static final long TEXT_PART_BYTES = 100L * 1024;
    /** 文本切分最大块数 */
    private static final int MAX_TEXT_PARTS = 20;
    /** 最大切分块数（防止切得过碎） */
    private static final int MAX_PARTS = 50;
    /** 块与块之间的提交限速间隔（毫秒），避免瞬时打爆 API 限流 */
    private static final long PART_INTERVAL_MS = 5000L;

    /** 单个子块：内容 + 文件名 */
    private record PartChunk(byte[] bytes, String name) {
    }

    /** 异步执行 LightRAG 解析（线程池执行，上传后立即返回） */
    @Async("docExecutor")
    public void parse(Document doc, byte[] content, String filename) {
        try {
            // 1. 切分大文件为多块（PDF 按页、文本按大小），小文件为单块
            List<PartChunk> parts = splitParts(content, filename);
            int total = parts.size();
            doc.setPartTotal(total);
            doc.setPartDone(0);
            documentMapper.updateById(doc);
            log.info("文档开始解析：{}，切分为 {} 块", doc.getOriginalName(), total);

            // 2. 逐块提交 + 轮询 + 限速
            for (int i = 0; i < total; i++) {
                PartChunk part = parts.get(i);
                String partName = total > 1 ? safePartName(part.name(), i + 1, total) : part.name();
                // 提交当前块（上传成功即计入进度）
                String trackId = lightRagClient.uploadDocument(part.bytes(), partName);
                doc.setTrackId(trackId);
                doc.setPartDone(i + 1);
                documentMapper.updateById(doc);
                updateStatus(doc, DocumentStatus.PROCESSING, uploadProgress(total, i + 1),
                        "已上传第 " + (i + 1) + "/" + total + " 块");
                // 轮询该块解析状态（进度条停在「已上传百分比」）
                parsePartAndWait(doc, i + 1, total);
                updateStatus(doc, DocumentStatus.PROCESSING, uploadProgress(total, i + 1),
                        "第 " + (i + 1) + "/" + total + " 块解析完成");
                // 限速：块间间隔，避免瞬时打爆 API 限流
                if (i < total - 1) {
                    sleepQuietly(PART_INTERVAL_MS);
                }
            }
            updateStatus(doc, DocumentStatus.DONE, 100, "解析完成");
        } catch (BizException e) {
            failDocument(doc, e.getMessage());
        } catch (Exception e) {
            log.error("文档解析异常：{}", doc.getOriginalName(), e);
            failDocument(doc, "解析失败，请稍后重试");
        }
    }

    /** 计算上传进度（已提交成功的块数占比，0-100） */
    private int uploadProgress(int total, int uploaded) {
        return (int) Math.min(100, uploaded * 100.0 / Math.max(1, total));
    }

    /** 切分文件为多个子块；md/txt 无论大小均切分，PDF 仅超大时切分 */
    private List<PartChunk> splitParts(byte[] content, String filename) throws IOException {
        String ext = extensionOf(filename);
        if ("md".equals(ext) || "txt".equals(ext)) {
            return splitText(content, filename);
        }
        if ("pdf".equals(ext) && content.length > SPLIT_THRESHOLD_BYTES) {
            return splitPdf(content, filename);
        }
        // 其他格式（docx/pptx 等）暂不支持安全切分，整文件上传
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

    /** 轮询单块解析状态直至完成 */
    private void parsePartAndWait(Document doc, int partIndex, int total) {
        int poll = 0;
        while (poll < MAX_POLL_TIMES) {
            Map<String, Object> status = lightRagClient.queryTrackStatus(doc.getTrackId());
            String state = resolveDocState(status);
            if (isDoneState(state)) {
                accumulateStats(doc, status);
                return;
            }
            if (isFailedState(state)) {
                throw new BizException(ErrorCode.FILE_ERROR,
                        "LightRAG 解析失败（第 " + partIndex + "/" + total + " 块）");
            }
            poll++;
            updateStatus(doc, DocumentStatus.PROCESSING,
                    uploadProgress(total, doc.getPartDone()),
                    "第 " + partIndex + "/" + total + " 块图谱构建中（向量检索已可用）");
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

    /** 将单块解析统计累加到文档（多块聚合） */
    private void accumulateStats(Document doc, Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.get(0);
            if (first instanceof Map<?, ?> docMap) {
                doc.setChunkCount(intOf(doc.getChunkCount()) + intOf(docMap.get("chunks_count")));
                doc.setEntityCount(intOf(doc.getEntityCount()) + intOf(docMap.get("entities_count")));
                doc.setRelationCount(intOf(doc.getRelationCount()) + intOf(docMap.get("relations_count")));
            }
        }
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
