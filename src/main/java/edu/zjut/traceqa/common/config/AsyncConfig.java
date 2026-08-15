package edu.zjut.traceqa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置。
 *
 * <p>提供两个线程池：</p>
 * <ul>
 *   <li>{@code ragExecutor} —— 聊天 RAG 编排（阻塞式 SSE 生成），核心线程适中；</li>
 *   <li>{@code docExecutor}  —— 文档异步解析队列，支持长耗时抽取任务。</li>
 * </ul>
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /** 文档解析专用线程池，丢弃策略保证不阻塞主线程 */
    @Bean("docExecutor")
    public Executor docExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-parser-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    /** 聊天 RAG 编排线程池 */
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