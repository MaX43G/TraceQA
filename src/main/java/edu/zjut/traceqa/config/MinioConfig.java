package edu.zjut.traceqa.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(AppProperties properties) {
        AppProperties.Minio cfg = properties.getMinio();
        return MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
    }
}