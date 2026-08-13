package com.smartsql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 *
 * 作业题2修复：知识库同步 AI 引擎在独立线程池执行，
 * 不占用处理 HTTP 请求的线程（Tomcat 默认 200 线程，被慢同步占满会拖垮所有接口）。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("knowledgeSyncExecutor")
    public Executor knowledgeSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);          // 同步任务不多，2 个核心线程足够
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);       // 排队 100 个任务，超出则拒绝（有 best-effort 兜底）
        executor.setThreadNamePrefix("knowledge-sync-");
        // 队列满时由调用方线程执行（保证同步不丢失），而非静默丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
