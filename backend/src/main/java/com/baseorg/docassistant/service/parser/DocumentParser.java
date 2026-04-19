package com.baseorg.docassistant.service.parser;

import java.io.InputStream;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 支持的文件类型
     */
    String getSupportedType();

    /**
     * 解析文档内容
     *
     * @param inputStream 文件输入流
     * @param filename    文件名
     * @return 解析后的纯文本内容
     */
    String parse(InputStream inputStream, String filename) throws Exception;

    /**
     * 是否支持该文件类型
     */
    default boolean supports(String fileType) {
        return getSupportedType().equalsIgnoreCase(fileType);
    }
}
