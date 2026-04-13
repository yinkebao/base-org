package com.baseorg.docassistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.baseorg.docassistant.mapper")
@SpringBootApplication
public class DocAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocAssistantApplication.class, args);
    }
}
