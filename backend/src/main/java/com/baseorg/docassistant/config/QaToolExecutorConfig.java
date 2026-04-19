package com.baseorg.docassistant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 工具并发执行线程池。
 * <p>
 * 采用 {@link ThreadPoolExecutor.CallerRunsPolicy} 作为拒绝策略：QA 是用户侧同步可感知的流程，
 * 丢任务会导致上下文缺失；让调用线程自行承担溢出任务可以形成自然背压，优于简单丢弃。<br>
 * {@code setWaitForTasksToCompleteOnShutdown(true)} 保证应用关闭时在途工具不被打断。
 */
@Configuration
@EnableConfigurationProperties(QaToolExecutorProperties.class)
@RequiredArgsConstructor
public class QaToolExecutorConfig {

    public static final String QA_TOOL_EXECUTOR_BEAN_NAME = "qaToolExecutor";

    private final QaToolExecutorProperties properties;

    @Bean(name = QA_TOOL_EXECUTOR_BEAN_NAME, destroyMethod = "shutdown")
    public Executor qaToolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }
}
