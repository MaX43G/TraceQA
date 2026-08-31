package edu.zjut.traceqa.qaservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 溯知 / TraceQA 问答服务启动类。
 *
 * <p>承担对话与 RAG 智能问答核心能力：会话消息管理、多 Agent 检索编排、
 * LLM 调用与熔断、SSE 流式推送。扫描 {@code edu.zjut.traceqa} 加载通用库。</p>
 */
@SpringBootApplication(scanBasePackages = "edu.zjut.traceqa")
@MapperScan("edu.zjut.traceqa.qaservice.mapper")
public class QaServiceApplication {

    /**
     * 问答服务启动入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(QaServiceApplication.class, args);
    }
}