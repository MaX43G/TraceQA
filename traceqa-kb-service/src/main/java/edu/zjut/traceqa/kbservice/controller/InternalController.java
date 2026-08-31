package edu.zjut.traceqa.kbservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.kbservice.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库服务内部接口（供管理服务等经 OpenFeign 调用，不经网关对外）。
 */
@Tag(name = "知识库服务内部接口", description = "供微服务间调用，不经网关对外")
@RestController
@RequestMapping("/internal")
public class InternalController {

    @Resource
    private DocumentService documentService;

    /**
     * 获取文档解析任务队列统计
     */
    @Operation(summary = "获取文档解析任务队列统计（内部调用）")
    @GetMapping("/queue/stats")
    public ApiResponse<Map<String, Object>> queueStats() {
        return ApiResponse.ok(documentService.queueStats());
    }
}