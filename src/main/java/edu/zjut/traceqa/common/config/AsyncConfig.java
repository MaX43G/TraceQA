package edu.zjut.traceqa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置。
 *
 * <p>提供聊天 RAG 编排线程池 {@code ragExecutor}（阻塞式 SSE 生成）。
 * 文档解析走 {@code DocumentQueueWorker} 独立守护线程 + Redis Stream 队列，不依赖 @Async。</p>
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * 聊天 RAG 编排线程池
     */
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