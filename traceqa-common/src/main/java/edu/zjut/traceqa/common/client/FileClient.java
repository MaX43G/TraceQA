package edu.zjut.traceqa.common.client;

import edu.zjut.traceqa.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 文件服务 Feign 客户端。
 *
 * <p>供其他微服务调用，将用户头像等文件字节写入 MinIO 对象存储并返回可访问 URL。</p>
 */
@FeignClient(name = "traceqa-file-service")
public interface FileClient {

    /**
     * 上传头像（字节流，供跨服务调用），返回 MinIO 公开访问 URL。
     *
     * @param data        头像图片字节
     * @param contentType 图片 MIME 类型
     * @return 统一响应，data 为头像 URL
     */
    @PostMapping(value = "/internal/avatar", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ApiResponse<String> uploadAvatar(@RequestBody byte[] data,
                                     @RequestHeader("X-Content-Type") String contentType);
}