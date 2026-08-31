package edu.zjut.traceqa.qaservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置。
 *
 * <p>提供 RAG 编排专用线程池 {@code ragExecutor}，用于承载阻塞式的 SSE 生成任务。</p>
 */
@Configuration
public class AsyncConfig {

    /** RAG 编排线程池 */
    @Bean("ragExecutor")
    public Executor ragExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rag-agent-");
        executor.initialize();
        return executor;
    }
}