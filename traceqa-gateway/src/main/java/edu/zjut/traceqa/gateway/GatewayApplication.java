package edu.zjut.traceqa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 溯知 / TraceQA 网关启动类。
 *
 * <p>基于 Spring Cloud Gateway + Nacos 提供服务发现与负载均衡路由，
 * 并通过 Sa-Token 统一完成登录鉴权。对外提供聚合后的 OpenAPI 接口文档。</p>
 */
@SpringBootApplication(scanBasePackages = "edu.zjut.traceqa.gateway")
public class GatewayApplication {

    /**
     * 网关启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}