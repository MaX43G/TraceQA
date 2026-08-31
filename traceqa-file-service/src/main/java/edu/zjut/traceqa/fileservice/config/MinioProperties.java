package edu.zjut.traceqa.fileservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置属性。
 */
@Data
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    /** MinIO 服务地址（内网连接，如 http://minio:9000） */
    private String endpoint = "http://localhost:6116";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 秘密密钥 */
    private String secretKey = "minioadmin";

    /** 存储桶名称 */
    private String bucket = "traceqa";

    /** 对外公开访问地址（HTTPS，供前端加载；为空则回退 endpoint） */
    private String publicUrl = "";
}