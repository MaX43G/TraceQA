package edu.zjut.traceqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.crypto.SecureUtil;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.AppProperties;
import edu.zjut.traceqa.model.vo.BatchUploadVO;
import edu.zjut.traceqa.model.vo.DocumentUploadVO;
import edu.zjut.traceqa.model.vo.DocumentVO;
import edu.zjut.traceqa.model.po.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
 * <p>负责文档上传（单文件 / zip 批量）、本地文件存储、内容指纹去重、查询与逻辑删除。
 * LightRAG 异步解析委托给 {@link DocumentParseWorker}（独立 Bean，保证 @Async 生效），
 * 上传接口立即返回 202，不阻塞请求线程。</p>
 */
@Slf4j
@Service
public class DocumentService {

    /**
     * 允许上传的文件扩展名集合（仅文本格式；PDF/PPT/Word 等需先转换为 Markdown）
     */
    private static final Set<String> ALLOWED_TYPES = Set.of("md", "txt");

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private AppProperties properties;
    @Resource
    private DocumentProgressStore progressStore;
    @Resource
    private DocumentQueueWorker documentQueueWorker;
    @Resource
    private DocumentParseWorker parseWorker;
    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文档并提交异步解析，立即返回。
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
     * 批量导入：解压 zip 内所有 .md/.txt，逐个入库解析
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
     * 校验是否标准 zip 归档：本地文件头 PK\x03\x04，或空压缩包 PK\x05\x06
     */
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

    /**
     * 核心上传逻辑（单文件/批量共用）：校验 -> 指纹去重 -> 落盘 -> 入库 -> 异步解析
     */
    private DocumentUploadVO uploadContent(String originalName, byte[] content, Long knowledgeBaseId) {
        knowledgeBaseService.requireById(knowledgeBaseId);
        String fileType = resolveFileType(originalName);
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的文件类型：" + fileType + "。PDF/PPT/Word/图片等格式请先用 MinerU 等工具转换为 Markdown（.md）后再上传");
        }
        // 内容指纹去重（相同内容视为重复）
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

        // 异步解析：任务入 Redis Stream 队列，由队列消费者线程执行
        documentQueueWorker.enqueue(doc.getId());
        log.info("文档上传成功：{}，kbId={}", originalName, knowledgeBaseId);
        return new DocumentUploadVO(doc.getId());
    }

    /**
     * 分页查询文档列表（查询时同步 LightRAG 刷新解析状态并回写数据库）
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
     * 查询某知识库下的全部文档（查询时同步 LightRAG 刷新解析状态并回写数据库）
     */
    public List<DocumentVO> listByKnowledgeBase(Long knowledgeBaseId) {
        return documentMapper.selectList(
                        new LambdaQueryWrapper<Document>()
                                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                                .orderByDesc(Document::getId))
                .stream().map(parseWorker::refresh).toList();
    }

    /**
     * 逻辑删除文档并清理本地文件
     */
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

    /**
     * 按需刷新文档解析状态（前端「刷新」按钮触发），返回最新文档信息
     */
    public DocumentVO refreshProgress(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return parseWorker.refresh(doc);
    }

    /**
     * 解析队列统计（待处理/处理中/死信），供管理后台可视化
     */
    public Map<String, Object> queueStats() {
        return documentQueueWorker.queueStats();
    }

    /**
     * 判断内容指纹是否已存在（去重）
     */
    private boolean existsContentHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        return documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getContentHash, hash)) > 0;
    }

    /**
     * 存储文件到本地文件系统
     */
    private Path storeFile(byte[] content, String originalName, Long knowledgeBaseId) {
        try {
            Path dir = Paths.get(properties.getStorage().getRoot()).resolve(String.valueOf(knowledgeBaseId));
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "")
                    + "_" + sanitizeFilename(originalName);
            Path target = dir.resolve(filename).normalize();
            // 路径穿越防护：确保最终路径仍在目标目录内
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

    /**
     * 清理本地文件
     */
    private void deleteLocalFile(String storedPath) {
        try {
            Files.deleteIfExists(Paths.get(storedPath));
        } catch (Exception e) {
            log.warn("本地文件删除失败：{}", storedPath);
        }
    }

    /**
     * 提取并规整文件扩展名
     */
    private String resolveFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件名不合法");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 文件名清洗（去路径分隔符）
     */
    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        // 移除路径分隔符、控制字符与路径穿越片段，避免任意文件写入
        String safe = name
                .replaceAll("[/\\\\]", "_")
                .replaceAll("\\.\\.", "_")
                .replaceAll("[\\x00-\\x1f]", "");
        // 去掉开头的点/下划线，避免隐藏文件与残留的 .. 
        safe = safe.replaceAll("^[\\._]+", "");
        return safe.isBlank() ? "file" : safe;
    }

    /**
     * 内容 SHA-256 指纹（Hutool）
     */
    private String sha256(byte[] content) {
        return SecureUtil.sha256(new String(content, StandardCharsets.UTF_8));
    }
}
