package com.baseorg.docassistant.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * PDF 文档解析器
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    @Override
    public String getSupportedType() {
        return "pdf";
    }

    @Override
    public String parse(InputStream inputStream, String filename) throws Exception {
        log.debug("开始解析 PDF 文件: {}", filename);

        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);
            log.debug("PDF 解析完成: {}, 页数: {}", filename, document.getNumberOfPages());

            return text.trim();
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }
}
