package edu.zjut.traceqa.userservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 溯知 / TraceQA 用户服务启动类。
 *
 * <p>负责用户注册登录与 RBAC 管理。扫描 {@code edu.zjut.traceqa} 以加载
 * 通用库（统一异常处理、用户上下文、MyBatis-Plus 配置等），
 * 并启用 OpenFeign 客户端以调用文件服务存储头像。</p>
 */
@SpringBootApplication(scanBasePackages = "edu.zjut.traceqa")
@EnableFeignClients(basePackages = "edu.zjut.traceqa.common.client")
@MapperScan("edu.zjut.traceqa.userservice.mapper")
public class UserServiceApplication {

    /**
     * 用户服务启动入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}