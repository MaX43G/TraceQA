package edu.zjut.traceqa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.model.vo.BatchUploadVO;
import edu.zjut.traceqa.model.vo.DocumentProgressVO;
import edu.zjut.traceqa.model.vo.DocumentUploadVO;
import edu.zjut.traceqa.model.vo.DocumentVO;
import edu.zjut.traceqa.service.DocumentProgressStore;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 文档接口。
 *
 * <p>上传接口返回 HTTP 202 Accepted + 任务 ID，后台异步执行 LightRAG 抽取；
 * 解析进度通过 SSE 实时推送（先补发快照，再按轮询推进）。</p>
 */
@Slf4j
@Tag(name = "文档", description = "文档上传、异步解析与进度追踪")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final long POLL_INTERVAL_MS = 2000L;

    @Resource
    private DocumentService documentService;
    @Resource
    private DocumentProgressStore progressStore;
    @Resource
    private ObjectMapper objectMapper;
    /** 进度 SSE 轮询线程（复用 docExecutor，避免重复自建线程池） */
    @Resource(name = "docExecutor")
    private Executor sseExecutor;

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

    @Operation(summary = "解析队列统计（待处理/处理中/死信）")
    @GetMapping("/queue/stats")
    public ApiResponse<Map<String, Object>> queueStats() {
        return ApiResponse.ok(documentService.queueStats());
    }

    @Operation(summary = "文档解析进度 SSE（先补发快照，再轮询推送）")
    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitter.complete());
        emitter.onTimeout(() -> emitter.complete());
        sseExecutor.execute(() -> pollAndPush(emitter, id));
        return emitter;
    }

    /** 轮询进度并推送，直至连接关闭或解析结束 */
    private void pollAndPush(SseEmitter emitter, Long documentId) {
        DocumentProgressVO lastSent = null;
        try {
            while (true) {
                DocumentProgressVO current = progressStore.get(documentId);
                if (current != null && !current.equals(lastSent)) {
                    push(emitter, current);
                    lastSent = current;
                }
                // 解析结束则退出；连接关闭时 send 会抛异常由外层捕获
                if (current != null && isTerminal(current.getStatus())) {
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (Exception e) {
            log.debug("文档进度 SSE 停止：docId={}, err={}", documentId, e.getMessage());
            emitter.complete();
        }
    }

    /** 推送单条进度事件 */
    private void push(SseEmitter emitter, DocumentProgressVO progress) throws Exception {
        emitter.send(SseEmitter.event().name("progress")
                .data(objectMapper.writeValueAsString(progress)));
    }

    /** 判断状态是否终结 */
    private boolean isTerminal(String status) {
        return "DONE".equals(status) || "FAILED".equals(status);
    }

    @Operation(summary = "逻辑删除文档")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.ok();
    }
}