package com.baseorg.docassistant.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.stream.Collectors;

/**
 * Word 文档解析器（支持 .docx）
 */
@Slf4j
@Component
public class WordParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "docx";
    }

    @Override
    public String parse(InputStream inputStream, String filename) throws Exception {
        log.debug("开始解析 Word 文件: {}", filename);

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            String text = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(para -> !para.isBlank())
                    .collect(Collectors.joining("\n"));

            log.debug("Word 解析完成: {}, 段落数: {}", filename, document.getParagraphs().size());

            return text.trim();
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType) || "doc".equalsIgnoreCase(fileType);
    }
}
