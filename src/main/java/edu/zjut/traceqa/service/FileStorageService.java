package edu.zjut.traceqa.service;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * MinIO 对象存储服务：上传头像等用户文件并返回可访问 URL。
 *
 * <p>自动建桶并将桶策略设为「公共只读」，保证头像可通过公开 URL 直接访问</p>
 */
@Slf4j
@Service
public class FileStorageService {

    /**
     * 桶公共只读策略（anonymous 读对象）
     */
    private static final String PUBLIC_READ_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":["
            + "{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
            + "\"Resource\":[\"arn:aws:s3:::%BUCKET%/*\"]}]}";

    @Resource
    private MinioClient minioClient;

    @Resource
    private AppProperties properties;

    /**
     * 上传头像（裁剪后的图片字节），返回可访问 URL
     */
    public String uploadAvatar(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片为空");
        }
        AppProperties.Minio cfg = properties.getMinio();
        String object = "avatars/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        try {
            ensureBucket(cfg);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(cfg.getBucket())
                    .object(object)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType == null || contentType.isBlank() ? "image/jpeg" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "文件上传失败");
        }
        String base = cfg.getPublicUrl() == null || cfg.getPublicUrl().isBlank() ? cfg.getEndpoint() : cfg.getPublicUrl();
        String url = base + "/" + cfg.getBucket() + "/" + object;
        log.info("头像上传完成：{}", object);
        return url;
    }

    /**
     * 确保桶存在并设置公共只读策略
     */
    public void ensureBucketConfigured() {
        AppProperties.Minio cfg = properties.getMinio();
        try {
            ensureBucket(cfg);
        } catch (Exception e) {
            log.warn("确保 MinIO 桶/策略失败：{}", e.getMessage());
        }
    }

    private void ensureBucket(AppProperties.Minio cfg) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(cfg.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(cfg.getBucket()).build());
        }
        // 设置公共只读策略，解决对象直链 403 Forbidden
        try {
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(cfg.getBucket())
                    .config(PUBLIC_READ_POLICY.replace("%BUCKET%", cfg.getBucket()))
                    .build());
        } catch (Exception e) {
            log.warn("设置桶公共只读策略失败（不影响上传，但公开访问可能受限）：{}", e.getMessage());
        }
    }
}