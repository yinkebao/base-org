package com.baseorg.docassistant.service.chunk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文档块
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSplitter {

    private String text;
    private int startOffset;
    private int endOffset;
    private int tokenCount;
    private String sectionTitle;
    private Map<String, Object> metadata;

    /**
     * 分块器接口
     */
    public interface Splitter {
        List<ChunkSplitter> split(String text);
    }
}
