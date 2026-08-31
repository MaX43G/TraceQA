package edu.zjut.traceqa.kbservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置。
 */
@Data
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 文件存储根目录 */
    private String root = "./data/files";
}