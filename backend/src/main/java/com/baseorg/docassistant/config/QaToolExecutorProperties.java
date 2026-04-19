package com.baseorg.docassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QA 工具执行线程池配置。
 * <p>
 * 工具执行阶段（PRE_RETRIEVAL / POST_RETRIEVAL）内的独立步骤会被并发提交至该线程池，
 * 以避免 WebSearch / MCP 等 IO 密集型工具的串行延迟叠加。
 */
@Data
@ConfigurationProperties(prefix = "app.qa.tool-executor")
public class QaToolExecutorProperties {

    /** 核心线程数。常态下常驻。 */
    private int corePoolSize = 4;

    /** 最大线程数。突发高峰时可扩容至此。 */
    private int maxPoolSize = 8;

    /** 任务队列容量。超过则由调用线程执行（CallerRunsPolicy）形成背压。 */
    private int queueCapacity = 50;

    /** 空闲线程回收秒数。 */
    private int keepAliveSeconds = 60;

    /** 线程名前缀，便于在 thread dump / 日志里识别来源。 */
    private String threadNamePrefix = "qa-tool-";

    /** 应用关闭时等待在途任务完成的最长秒数。 */
    private int awaitTerminationSeconds = 10;
}
