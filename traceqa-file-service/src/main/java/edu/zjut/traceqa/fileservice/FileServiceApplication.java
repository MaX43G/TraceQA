package edu.zjut.traceqa.fileservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 溯知 / TraceQA 文件服务启动类。
 *
 * <p>基于 MinIO 对象存储统一管理用户上传文件，对外提供上传下载接口，
 * 并暴露内部上传端点供其他微服务经 OpenFeign 调用。</p>
 */
@SpringBootApplication(scanBasePackages = "edu.zjut.traceqa")
public class FileServiceApplication {

    /**
     * 文件服务启动入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}