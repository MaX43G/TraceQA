package edu.zjut.traceqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.AppProperties;
import edu.zjut.traceqa.dto.document.DocumentProgressVO;
import edu.zjut.traceqa.dto.document.DocumentUploadVO;
import edu.zjut.traceqa.dto.document.DocumentVO;
import edu.zjut.traceqa.entity.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文档服务。
 *
 * <p>负责文档上传、本地文件存储、查询与逻辑删除。
 * LightRAG 异步解析委托给 {@link DocumentParseWorker}（独立 Bean，保证 @Async 生效），
 * 上传接口立即返回 202，不阻塞请求线程。</p>
 */
@Slf4j
@Service
public class DocumentService {

    /** 允许上传的文件扩展名集合 */
    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "pptx", "ppt", "docx", "doc", "md", "txt");

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private AppProperties properties;
    @Resource
    private DocumentProgressStore progressStore;
    @Resource
    private DocumentParseWorker parseWorker;
    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文档并提交异步解析，立即返回。
     */
    public DocumentUploadVO upload(MultipartFile file, Long knowledgeBaseId) {
        knowledgeBaseService.requireById(knowledgeBaseId);
        String fileType = resolveFileType(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的文件类型：" + fileType);
        }
        // 同步读取文件字节（transferTo 会移动 Tomcat 临时文件，异步线程中再读将失败）
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("文件读取失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "文件读取失败");
        }
        Path storedPath = storeFile(file, knowledgeBaseId);

        Document doc = new Document();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setOriginalName(file.getOriginalFilename());
        doc.setStoredPath(storedPath.toString());
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setStatus(DocumentStatus.PENDING.name());
        documentMapper.insert(doc);

        // 异步解析（跨 Bean 调用，@Async 代理生效，不阻塞本方法）
        parseWorker.parse(doc, content, file.getOriginalFilename());
        log.info("文档上传成功：{}，kbId={}", doc.getOriginalName(), knowledgeBaseId);
        return new DocumentUploadVO(doc.getId());
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
        String safe = name == null ? "file" : name.replaceAll("[/\\\\]", "_");
        return safe.isBlank() ? "file" : safe;
    }
}