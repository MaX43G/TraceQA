package edu.zjut.traceqa.kbservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 溯知 / TraceQA 知识库服务启动类。
 *
 * <p>负责知识库与文档管理，文档经 Redis 队列异步解析并写入 LightRAG。
 * 扫描 {@code edu.zjut.traceqa} 以加载通用库与共享的 LightRAG 客户端。</p>
 */
@SpringBootApplication(scanBasePackages = "edu.zjut.traceqa")
@MapperScan("edu.zjut.traceqa.kbservice.mapper")
public class KbServiceApplication {

    /**
     * 知识库服务启动入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(KbServiceApplication.class, args);
    }
}