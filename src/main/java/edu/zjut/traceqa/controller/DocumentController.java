package edu.zjut.traceqa.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.model.vo.BatchUploadVO;
import edu.zjut.traceqa.model.vo.DocumentUploadVO;
import edu.zjut.traceqa.model.vo.DocumentVO;
import edu.zjut.traceqa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档接口。
 *
 */
@Slf4j
@Tag(name = "文档", description = "文档上传、异步解析与进度追踪")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @Operation(summary = "上传文档（异步解析，立即返回 202）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadVO>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        DocumentUploadVO vo = documentService.upload(file, knowledgeBaseId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(vo));
    }

    @Operation(summary = "批量导入文档（zip 压缩包，内含 .md/.txt）")
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchUploadVO> batchUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        return ApiResponse.ok(documentService.batchUpload(file, knowledgeBaseId));
    }

    @Operation(summary = "分页查询文档列表")
    @GetMapping
    public ApiResponse<PageResult<DocumentVO>> page(
            @RequestParam(required = false) Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(documentService.page(knowledgeBaseId, page, size));
    }

    @Operation(summary = "查询知识库下全部文档")
    @GetMapping("/by-kb")
    public ApiResponse<List<DocumentVO>> listByKb(@RequestParam Long knowledgeBaseId) {
        return ApiResponse.ok(documentService.listByKnowledgeBase(knowledgeBaseId));
    }

    @Operation(summary = "按需刷新文档解析状态（后端不轮询，由用户触发时查询 LightRAG）")
    @PostMapping("/{id}/refresh")
    public ApiResponse<DocumentVO> refresh(@PathVariable Long id) {
        return ApiResponse.ok(documentService.refreshProgress(id));
    }

    @Operation(summary = "逻辑删除文档")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.ok();
    }
}