package com.baseorg.docassistant.service.chunk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmartChunkSplitterTest {

    private SmartChunkSplitter splitter;

    @BeforeEach
    void setUp() throws Exception {
        splitter = new SmartChunkSplitter();
        setField("maxChunkSize", 30);
        setField("overlapSize", 6);
        setField("minChunkSize", 1);
        setField("tokenizerEncoding", "cl100k_base");
    }

    @Test
    void shouldSplitStructuredTextByRealTokens() {
        String text = """
                # 第一章
                这是第一章的介绍内容，包含多个句子，用来验证真正的 token 分块逻辑。

                ## 第二节
                这里继续补充更多正文，确保切块时会按照 token 上限拆分，而不是按字符数粗略估算。
                """;

        List<ChunkSplitter> chunks = splitter.split(text, new SmartChunkSplitter.ChunkOptions(20, 5, true));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getTokenCount()).isLessThanOrEqualTo(25));
        assertThat(chunks.stream().map(ChunkSplitter::getSectionTitle)).contains("第一章", "第二节");
    }

    @Test
    void shouldSplitUnstructuredTextWithOverlap() {
        String text = "这是一个没有标题的长段落。".repeat(20);

        List<ChunkSplitter> chunks = splitter.split(text, new SmartChunkSplitter.ChunkOptions(18, 4, false));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).getSectionTitle()).isNull();
        assertThat(chunks.get(1).getText()).contains(chunks.get(0).getText().substring(chunks.get(0).getText().length() - 2));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = SmartChunkSplitter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(splitter, value);
    }
}
