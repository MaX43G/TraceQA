package edu.zjut.traceqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.AppProperties;
import edu.zjut.traceqa.config.LightRagClient;
import edu.zjut.traceqa.dto.document.DocumentProgressVO;
import edu.zjut.traceqa.dto.document.DocumentUploadVO;
import edu.zjut.traceqa.dto.document.DocumentVO;
import edu.zjut.traceqa.entity.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档服务。
 *
 * <p>负责文档上传、本地文件存储、异步解析队列与进度追踪。上传接口立即返回
 * 202 Accepted（{@code DocumentUploadVO}），后台通过 {@code docExecutor} 异步执行
 * LightRAG 抽取，前端通过进度 SSE 面板观察解析状态。</p>
 */
@Slf4j
@Service
public class DocumentService {

    /** 允许上传的文件扩展名集合 */
    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "pptx", "ppt", "docx", "doc", "md", "txt");
    /** 解析轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 2000L;
    /** 解析轮询最大次数（约 10 分钟） */
    private static final int MAX_POLL_TIMES = 300;

    private final DocumentMapper documentMapper;
    private final LightRagClient lightRagClient;
    private final AppProperties properties;
    private final DocumentProgressStore progressStore;
    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentService(DocumentMapper documentMapper, LightRagClient lightRagClient,
                           AppProperties properties, DocumentProgressStore progressStore,
                           KnowledgeBaseService knowledgeBaseService) {
        this.documentMapper = documentMapper;
        this.lightRagClient = lightRagClient;
        this.properties = properties;
        this.progressStore = progressStore;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 上传文档并提交异步解析。
     *
     * @return 文档 ID + 解析任务 ID（立即返回，不阻塞）
     */
    public DocumentUploadVO upload(MultipartFile file, Long knowledgeBaseId) {
        // 校验知识库存在
        knowledgeBaseService.requireById(knowledgeBaseId);
        // 校验文件扩展名
        String fileType = resolveFileType(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的文件类型：" + fileType);
        }
        // 本地存储
        Path storedPath = storeFile(file, knowledgeBaseId);
        // 落库
        Document doc = new Document();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setOriginalName(file.getOriginalFilename());
        doc.setStoredPath(storedPath.toString());
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setStatus(DocumentStatus.PENDING.name());
        documentMapper.insert(doc);

        // 注册进度占位并异步解析
        DocumentProgressVO initial = buildProgress(doc, 5, null, "已上传，等待解析");
        progressStore.register(doc.getId(), initial);
        runParseTask(doc, file);

        log.info("文档上传成功：{}，kbId={}", doc.getOriginalName(), knowledgeBaseId);
        return new DocumentUploadVO(doc.getId(), String.valueOf(doc.getId()));
    }

    /** 异步执行 LightRAG 解析（线程池执行） */
    @Async("docExecutor")
    public void runParseTask(Document doc, MultipartFile file) {
        try {
            updateStatus(doc, DocumentStatus.PROCESSING, 15, null, "正在提交至知识图谱引擎");
            // 1. 提交 LightRAG 上传
            String trackId = lightRagClient.uploadDocument(file);
            doc.setTrackId(trackId);
            documentMapper.updateById(doc);
            updateStatus(doc, DocumentStatus.PROCESSING, 30, null, "正在抽取文本与知识图谱");

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
            // LightRAG 该版本状态值：processed / failed / processing（兼容 completed/success/error）
            if (isDoneState(state)) {
                applyStats(doc, status);
                updateStatus(doc, DocumentStatus.DONE, 100, status, "解析完成");
                return;
            }
            if (isFailedState(state)) {
                throw new BizException(ErrorCode.FILE_ERROR, "LightRAG 解析失败");
            }
            // 仍在处理，按轮询次数推进进度（30% -> 90%）
            poll++;
            int progress = Math.min(90, 30 + poll);
            updateStatus(doc, DocumentStatus.PROCESSING, progress, null, "正在构建知识图谱");
            sleepQuietly(POLL_INTERVAL_MS);
        }
        throw new BizException(ErrorCode.FILE_ERROR, "解析超时，请稍后重试");
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

    /** 从 LightRAG 状态响应中解析文档状态（取 documents 数组首个元素） */
    private String resolveDocState(Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.get(0);
            if (first instanceof Map<?, ?> docMap && docMap.get("status") != null) {
                return String.valueOf(docMap.get("status"));
            }
        }
        // 兼容旧结构：顶层 status 字段
        Object state = status.get("status");
        return state == null ? "processing" : String.valueOf(state);
    }

    /** 从 LightRAG 状态结果中提取统计数据（documents 首个元素携带 chunks_count） */
    private void applyStats(Document doc, Map<String, Object> status) {
        Object docs = status.get("documents");
        if (docs instanceof List<?> documentList && !documentList.isEmpty()) {
            Object first = documentList.get(0);
            if (first instanceof Map<?, ?> docMap) {
                doc.setChunkCount(intOf(docMap.get("chunks_count")));
            }
        }
    }

    /** 分页查询文档列表 */
    public PageResult<DocumentVO> page(Long knowledgeBaseId, long page, long size) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(knowledgeBaseId != null, Document::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(Document::getId);
        IPage<Document> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, DocumentVO::of);
    }

    /** 查询某知识库下的全部文档 */
    public List<DocumentVO> listByKnowledgeBase(Long knowledgeBaseId) {
        return documentMapper.selectList(
                        new LambdaQueryWrapper<Document>()
                                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                                .orderByDesc(Document::getId))
                .stream().map(DocumentVO::of).toList();
    }

    /** 逻辑删除文档并清理本地文件 */
    public void delete(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        documentMapper.deleteById(id);
        progressStore.remove(id);
        deleteLocalFile(doc.getStoredPath());
        log.info("文档已删除：{}", doc.getOriginalName());
    }

    /** 获取文档进度快照（供 SSE 重连补发） */
    public DocumentProgressVO getProgress(Long documentId) {
        return progressStore.get(documentId);
    }

    /** 校验并更新文档状态与进度 */
    private void updateStatus(Document doc, DocumentStatus status, int progress,
                              Map<String, Object> detail, String message) {
        doc.setStatus(status.name());
        documentMapper.updateById(doc);
        DocumentProgressVO vo = buildProgress(doc, progress, null, message);
        progressStore.update(vo);
    }

    /** 标记解析失败 */
    private void failDocument(Document doc, String message) {
        doc.setStatus(DocumentStatus.FAILED.name());
        doc.setErrorMsg(message);
        documentMapper.updateById(doc);
        progressStore.update(buildProgress(doc, 100, null, message));
        log.warn("文档解析失败：{}，原因：{}", doc.getOriginalName(), message);
    }

    /** 组装进度 DTO */
    private DocumentProgressVO buildProgress(Document doc, int progress, Object unused, String message) {
        return new DocumentProgressVO(doc.getId(), doc.getTrackId(), doc.getStatus(),
                progress, doc.getChunkCount(), doc.getEntityCount(), doc.getRelationCount(), message);
    }

    /** 存储文件到本地文件系统 */
    private Path storeFile(MultipartFile file, Long knowledgeBaseId) {
        try {
            Path dir = Paths.get(properties.getStorage().getRoot()).resolve(String.valueOf(knowledgeBaseId));
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "")
                    + "_" + sanitizeFilename(file.getOriginalFilename());
            Path target = dir.resolve(filename);
            file.transferTo(target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            log.error("文件存储失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "文件保存失败");
        }
    }

    /** 清理本地文件 */
    private void deleteLocalFile(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (Exception e) {
            log.warn("本地文件删除失败：{}", storedPath);
        }
    }

    /** 提取并规整文件扩展名 */
    private String resolveFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件名不合法");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /** 文件名清洗（去路径分隔符） */
    private String sanitizeFilename(String name) {
        String safe = name.replaceAll("[/\\\\]", "_");
        return safe == null ? "file" : safe;
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