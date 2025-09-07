package com.leon.marketservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Configuration for asynchronous processing
 * Sets up thread pools for background tasks and async operations
 * 
 * This configuration provides thread pools for scheduled tasks,
 * async operations, and background processing.
 */
@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * Task executor for async operations
     * Provides thread pool for asynchronous operations
     * 
     * @return ThreadPoolTaskExecutor for async operations
     */
    @Bean("taskExecutor")
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 20
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("Async-")
        executor.initialize()
        return executor
    }

    /**
     * Scheduled task executor
     * Provides thread pool for scheduled tasks
     * 
     * @return ThreadPoolTaskExecutor for scheduled tasks
     */
    @Bean("scheduledTaskExecutor")
    fun scheduledTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 3
        executor.maxPoolSize = 10
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("Scheduled-")
        executor.initialize()
        return executor
    }
}
