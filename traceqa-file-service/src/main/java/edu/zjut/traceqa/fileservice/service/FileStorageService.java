package edu.zjut.traceqa.fileservice.service;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.fileservice.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * MinIO 对象存储服务：上传头像等用户文件并返回可访问 URL。
 *
 * <p>自动建桶并将桶策略设为「公共只读」，保证头像等文件可通过公开 URL 直接访问。</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /**
     * 桶公共只读策略（anonymous 读对象）
     */
    private static final String PUBLIC_READ_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":["
            + "{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
            + "\"Resource\":[\"arn:aws:s3:::%BUCKET%/*\"]}]}";

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public FileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 启动时确保桶存在并配置公共只读策略
     */
    @PostConstruct
    public void init() {
        ensureBucketConfigured();
    }

    /**
     * 上传头像（裁剪后的图片字节），返回可访问 URL。
     *
     * @param data        图片字节
     * @param contentType 图片 MIME 类型
     * @return 头像公开访问 URL
     */
    public String uploadAvatar(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片为空");
        }
        String object = "avatars/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(object)
                    .stream(new ByteArrayInputStream(data), (long) data.length, -1L)
                    .contentType(contentType == null || contentType.isBlank() ? "image/jpeg" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "文件上传失败");
        }
        String base = properties.getPublicUrl() == null || properties.getPublicUrl().isBlank()
                ? properties.getEndpoint() : properties.getPublicUrl();
        String url = base + "/" + properties.getBucket() + "/" + object;
        log.info("头像上传完成：{}", object);
        return url;
    }

    /**
     * 确保桶存在并设置公共只读策略
     */
    public void ensureBucketConfigured() {
        try {
            ensureBucket();
        } catch (Exception e) {
            log.warn("确保 MinIO 桶/策略失败：{}", e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
        }
        try {
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(properties.getBucket())
                    .config(PUBLIC_READ_POLICY.replace("%BUCKET%", properties.getBucket()))
                    .build());
        } catch (Exception e) {
            log.warn("设置桶公共只读策略失败（不影响上传，但公开访问可能受限）：{}", e.getMessage());
        }
    }
}