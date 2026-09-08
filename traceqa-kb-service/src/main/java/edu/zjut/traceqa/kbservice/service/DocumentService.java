package edu.zjut.traceqa.kbservice.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.Document;
import edu.zjut.traceqa.common.model.po.EsChunk;
import edu.zjut.traceqa.common.model.vo.BatchUploadVO;
import edu.zjut.traceqa.common.model.vo.DocumentUploadVO;
import edu.zjut.traceqa.common.model.vo.DocumentVO;
import edu.zjut.traceqa.common.repository.EsChunkRepository;
import edu.zjut.traceqa.kbservice.config.StorageProperties;
import edu.zjut.traceqa.kbservice.mapper.DocumentMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档服务。
 *
 * <p>负责文档上传（含 zip 批量导入）、去重、本地存储、异步解析入队、
 * 分页查询与进度刷新。文件本体存于本地文件系统，数据库仅保存路径引用。</p>
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /**
     * 允许上传的文件类型（其余格式需先转换为 Markdown）
     */
    private static final Set<String> ALLOWED_TYPES = Set.of("md", "txt");

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private StorageProperties properties;
    @Resource
    private DocumentProgressStore progressStore;
    @Resource
    private DocumentQueueWorker documentQueueWorker;
    @Resource
    private DocumentParseWorker parseWorker;
    @Resource
    private KnowledgeBaseService knowledgeBaseService;
    @Resource
    private EsChunkRepository esChunkRepository;

    /**
     * 单文档上传
     */
    public DocumentUploadVO upload(MultipartFile file, Long knowledgeBaseId) {
        try {
            byte[] content = file.getBytes();
            return uploadContent(file.getOriginalFilename(), content, knowledgeBaseId);
        } catch (IOException e) {
            log.error("文件读取失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "文件读取失败");
        }
    }

    /**
     * 批量导入（zip 压缩包，内含 .md/.txt）
     */
    public BatchUploadVO batchUpload(MultipartFile zipFile, Long knowledgeBaseId) {
        knowledgeBaseService.requireById(knowledgeBaseId);
        byte[] archive;
        try {
            archive = zipFile.getBytes();
        } catch (IOException e) {
            log.error("批量导入压缩包读取失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "压缩包读取失败");
        }
        if (!isZipArchive(archive)) {
            throw new BizException(ErrorCode.FILE_ERROR,
                    "不是有效的 zip 压缩包（应为 .zip，内含 .md/.txt 文档）。请勿上传 md/txt/7z/rar 或重命名文件冒充 zip");
        }
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.startsWith("\\") || name.contains("..")) {
                    failed++;
                    errors.add(name + "：非法文件名（拒绝路径穿越）");
                    continue;
                }
                try {
                    byte[] content = zis.readAllBytes();
                    uploadContent(name, content, knowledgeBaseId);
                    success++;
                } catch (BizException e) {
                    failed++;
                    errors.add(name + "：" + e.getMessage());
                } catch (Exception e) {
                    failed++;
                    errors.add(name + "：解析失败");
                }
            }
        } catch (IOException e) {
            log.error("批量导入压缩包解析失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "压缩包解析失败：" + e.getMessage());
        }
        if (success == 0 && failed == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "压缩包内没有可导入的 .md/.txt 文件");
        }
        log.info("批量导入完成：成功 {}，失败 {}", success, failed);
        return new BatchUploadVO(success, failed, errors);
    }

    /**
     * 分页查询文档（查询时同步刷新解析状态）
     */
    public PageResult<DocumentVO> page(Long knowledgeBaseId, long page, long size) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(knowledgeBaseId != null, Document::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(Document::getId);
        IPage<Document> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        List<DocumentVO> records = result.getRecords().stream().map(parseWorker::refresh).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    /**
     * 查询知识库下全部文档
     */
    public List<DocumentVO> listByKnowledgeBase(Long knowledgeBaseId) {
        return documentMapper.selectList(
                        new LambdaQueryWrapper<Document>()
                                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                                .orderByDesc(Document::getId))
                .stream().map(parseWorker::refresh).toList();
    }

    /**
     * 逻辑删除文档（同时删除本地文件与进度记录）
     */
    public void delete(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        documentMapper.deleteById(id);
        progressStore.remove(id);
        deleteLocalFile(doc.getStoredPath());
        // 清理 ES 索引
        try {
            esChunkRepository.deleteByDocumentId(id);
        } catch (Exception e) {
            log.warn("ES 清理失败（不影响文档删除）：docId={}, err={}", id, e.getMessage());
        }
        log.info("文档已删除：{}", doc.getOriginalName());
    }

    /**
     * 按需刷新文档解析状态
     */
    public DocumentVO refreshProgress(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return parseWorker.refresh(doc);
    }

    /**
     * 队列统计
     */
    public Map<String, Object> queueStats() {
        return documentQueueWorker.queueStats();
    }

    /**
     * 将所有已完成文档重建到 ES 索引（一次性迁移）
     *
     * @param knowledgeBaseId 指定知识库 ID（null 表示全部）
     * @return 索引的文档数
     */
    public int reindexEs(Long knowledgeBaseId) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(knowledgeBaseId != null, Document::getKnowledgeBaseId, knowledgeBaseId)
                .eq(Document::getStatus, DocumentStatus.DONE.name());
        List<Document> docs = documentMapper.selectList(wrapper);
        int indexed = 0;
        for (Document doc : docs) {
            try {
                byte[] content = readLocalFile(doc.getStoredPath());
                if (content == null || content.length == 0) {
                    log.warn("跳过空文件：{}", doc.getOriginalName());
                    continue;
                }
                // 先清理旧的 ES 索引
                esChunkRepository.deleteByDocumentId(doc.getId());
                // 切段并索引
                List<EsChunk> chunks = parseWorker.splitForEs(content, doc.getId(), doc.getKnowledgeBaseId(), doc.getOriginalName());
                esChunkRepository.indexAll(chunks);
                indexed++;
                log.info("ES reindex 完成：{}，chunk 数={}", doc.getOriginalName(), chunks.size());
            } catch (Exception e) {
                log.error("ES reindex 失败：{}, err={}", doc.getOriginalName(), e.getMessage(), e);
            }
        }
        log.info("ES reindex 全部完成：共 {} 个文档", indexed);
        return indexed;
    }

    private byte[] readLocalFile(String storedPath) {
        try {
            return Files.readAllBytes(Paths.get(storedPath));
        } catch (Exception e) {
            log.warn("读取本地文件失败：{}, err={}", storedPath, e.getMessage());
            return null;
        }
    }

    private DocumentUploadVO uploadContent(String originalName, byte[] content, Long knowledgeBaseId) {
        knowledgeBaseService.requireById(knowledgeBaseId);
        String fileType = resolveFileType(originalName);
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的文件类型：" + fileType + "。PDF/PPT/Word/图片等格式请先用 MinerU 等工具转换为 Markdown（.md）后再上传");
        }
        String contentHash = sha256(content);
        if (existsContentHash(contentHash)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该文档内容已存在，请勿重复上传：" + originalName);
        }
        Path storedPath = storeFile(content, originalName, knowledgeBaseId);

        Document doc = Document.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .originalName(originalName)
                .storedPath(storedPath.toString())
                .fileType(fileType)
                .contentHash(contentHash)
                .fileSize((long) content.length)
                .status(DocumentStatus.PENDING.name())
                .build();
        documentMapper.insert(doc);

        documentQueueWorker.enqueue(doc.getId());
        log.info("文档上传成功：{}，kbId={}", originalName, knowledgeBaseId);
        return new DocumentUploadVO(doc.getId());
    }

    private boolean existsContentHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        return documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getContentHash, hash)) > 0;
    }

    private Path storeFile(byte[] content, String originalName, Long knowledgeBaseId) {
        try {
            Path dir = Paths.get(properties.getRoot()).resolve(String.valueOf(knowledgeBaseId));
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "")
                    + "_" + sanitizeFilename(originalName);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "非法的文件名");
            }
            Files.write(target, content);
            return target;
        } catch (IOException e) {
            log.error("文件存储失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "文件保存失败");
        }
    }

    private void deleteLocalFile(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (Exception e) {
            log.warn("本地文件删除失败：{}", storedPath);
        }
    }

    private String resolveFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件名不合法");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String safe = name
                .replaceAll("[/\\\\]", "_")
                .replaceAll("\\.\\.", "_")
                .replaceAll("[\\x00-\\x1f]", "");
        safe = safe.replaceAll("^[\\._]+", "");
        return safe.isBlank() ? "file" : safe;
    }

    private String sha256(byte[] content) {
        return SecureUtil.sha256(new String(content, StandardCharsets.UTF_8));
    }

    private boolean isZipArchive(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        boolean localHeader = (data[0] & 0xFF) == 0x50 && (data[1] & 0xFF) == 0x4B
                && (data[2] & 0xFF) == 0x03 && (data[3] & 0xFF) == 0x04;
        boolean emptyArchive = (data[0] & 0xFF) == 0x50 && (data[1] & 0xFF) == 0x4B
                && (data[2] & 0xFF) == 0x05 && (data[3] & 0xFF) == 0x06;
        return localHeader || emptyArchive;
    }
}