package edu.zjut.traceqa.adminservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 溯知 / TraceQA 管理服务启动类。
 *
 * <p>负责系统公告、监控聚合、健康检查与可观测性反向代理。
 * 启用 OpenFeign 以拉取知识库/问答服务的运行指标。</p>
 */
@SpringBootApplication(scanBasePackages = "edu.zjut.traceqa")
@EnableFeignClients(basePackages = "edu.zjut.traceqa.common.client")
@MapperScan("edu.zjut.traceqa.adminservice.mapper")
public class AdminServiceApplication {

    /**
     * 管理服务启动入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}