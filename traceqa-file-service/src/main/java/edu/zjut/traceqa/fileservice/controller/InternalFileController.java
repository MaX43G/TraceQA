package edu.zjut.traceqa.fileservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.fileservice.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件服务内部接口（供其他微服务经 OpenFeign 调用，不对外经网关暴露）。
 */
@Tag(name = "文件服务内部接口", description = "供微服务间调用，不经网关对外")
@RestController
@RequestMapping("/internal")
public class InternalFileController {

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 上传头像（字节流，服务间调用），返回 MinIO 公开 URL
     */
    @Operation(summary = "上传头像（内部调用）")
    @PostMapping(value = "/avatar", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<String> uploadAvatar(@RequestBody byte[] data,
                                            @RequestHeader(value = "X-Content-Type", required = false) String contentType) {
        return ApiResponse.ok(fileStorageService.uploadAvatar(data, contentType));
    }
}