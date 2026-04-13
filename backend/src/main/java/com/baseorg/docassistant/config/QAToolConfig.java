package com.baseorg.docassistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * QA 工具装配配置。
 */
@Configuration
@EnableConfigurationProperties({AppQaToolProperties.class, AppQaWebSearchProperties.class, AppQaMcpProperties.class})
public class QAToolConfig {
}
