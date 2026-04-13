package com.baseorg.docassistant.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Markdown 文档解析器
 */
@Slf4j
@Component
public class MarkdownParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "md";
    }

    @Override
    public String parse(InputStream inputStream, String filename) throws Exception {
        log.debug("开始解析 Markdown 文件: {}", filename);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String text = reader.lines()
                    .collect(Collectors.joining("\n"));

            log.debug("Markdown 解析完成: {}", filename);

            return text.trim();
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType);
    }
}
