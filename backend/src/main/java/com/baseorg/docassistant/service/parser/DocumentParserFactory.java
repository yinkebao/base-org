package com.baseorg.docassistant.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档解析器工厂
 */
@Slf4j
@Component
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserMap;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parserMap = parsers.stream()
                .collect(Collectors.toMap(
                        DocumentParser::getSupportedType,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
        log.info("已注册文档解析器: {}", parserMap.keySet());
    }

    /**
     * 获取指定文件类型的解析器
     *
     * @param fileType 文件类型（不含点号）
     * @return 对应的解析器，如果没有则返回 null
     */
    public DocumentParser getParser(String fileType) {
        if (fileType == null) {
            return null;
        }

        String normalizedType = fileType.toLowerCase().replace(".", "");

        // 直接匹配
        DocumentParser parser = parserMap.get(normalizedType);
        if (parser != null) {
            return parser;
        }

        // 遍历查找支持的解析器
        for (DocumentParser p : parserMap.values()) {
            if (p.supports(normalizedType)) {
                return p;
            }
        }

        return null;
    }

    /**
     * 检查是否支持该文件类型
     */
    public boolean isSupported(String fileType) {
        return getParser(fileType) != null;
    }
}
