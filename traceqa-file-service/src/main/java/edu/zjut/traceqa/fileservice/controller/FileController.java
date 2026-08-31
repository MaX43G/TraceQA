package edu.zjut.traceqa.fileservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.fileservice.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传接口（对外）。
 */
@Tag(name = "文件", description = "统一文件上传（MinIO 对象存储）")
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 上传头像（直接调用文件服务），返回 MinIO 公开 URL
     */
    @Operation(summary = "上传头像")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(fileStorageService.uploadAvatar(file.getBytes(), file.getContentType()));
    }
}