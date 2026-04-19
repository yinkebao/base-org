package com.baseorg.docassistant.service.chunk;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能文档分块策略
 * 支持按段落、标题、Token 数量进行分块
 */
@Slf4j
@Component
public class SmartChunkSplitter implements ChunkSplitter.Splitter {

    @Value("${app.chunk.max-size:1000}")
    private int maxChunkSize;

    @Value("${app.chunk.overlap-size:200}")
    private int overlapSize;

    @Value("${app.chunk.min-size:100}")
    private int minChunkSize;

    @Value("${app.chunk.tokenizer-encoding:cl100k_base}")
    private String tokenizerEncoding;

    // 匹配 Markdown 标题
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    // 匹配段落分隔（双换行）
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\s*\\n");

    private volatile Encoding encoding;

    @Override
    public List<ChunkSplitter> split(String text) {
        return split(text, new ChunkOptions(maxChunkSize, overlapSize, true));
    }

    public List<ChunkSplitter> split(String text, ChunkOptions options) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        ChunkOptions effectiveOptions = options == null
                ? new ChunkOptions(maxChunkSize, overlapSize, true)
                : options;

        log.debug("开始分块，文本长度={}, chunkSize={}, overlap={}, structured={}",
                text.length(), effectiveOptions.sizeTokens(), effectiveOptions.overlapTokens(),
                effectiveOptions.structuredChunk());

        List<Section> sections = effectiveOptions.structuredChunk()
                ? splitByHeaders(text)
                : List.of(new Section(null, text.trim(), 0, text.length()));

        List<ChunkSplitter> chunks = new ArrayList<>();
        for (Section section : sections) {
            if (section.content == null || section.content.isBlank()) {
                continue;
            }
            if (countTokens(section.content) <= effectiveOptions.sizeTokens()) {
                mergeOrAppend(chunks, createChunk(section.content, section.title, section.start, section.end), effectiveOptions);
                continue;
            }
            splitLongSection(section, effectiveOptions).forEach(chunk -> mergeOrAppend(chunks, chunk, effectiveOptions));
        }

        List<ChunkSplitter> overlapped = addOverlap(chunks, effectiveOptions.overlapTokens());
        log.debug("分块完成，共 {} 个块", overlapped.size());
        return overlapped;
    }

    /**
     * 按标题分割文档
     */
    private List<Section> splitByHeaders(String text) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(text);

        int lastEnd = 0;
        String lastTitle = null;

        while (matcher.find()) {
            if (lastEnd > 0 || matcher.start() > 0) {
                String content = text.substring(lastEnd, matcher.start()).trim();
                if (!content.isEmpty()) {
                    sections.add(new Section(lastTitle, content, lastEnd, matcher.start()));
                }
            }
            lastTitle = matcher.group(2).trim();
            lastEnd = matcher.end();
        }

        // 最后一个段落
        if (lastEnd < text.length()) {
            String content = text.substring(lastEnd).trim();
            if (!content.isEmpty()) {
                sections.add(new Section(lastTitle, content, lastEnd, text.length()));
            }
        }

        // 如果没有标题，整个文档作为一个段落
        if (sections.isEmpty()) {
            sections.add(new Section(null, text.trim(), 0, text.length()));
        }

        return sections;
    }

    /**
     * 分割过长的段落
     */
    private List<ChunkSplitter> splitLongSection(Section section, ChunkOptions options) {
        List<ChunkSplitter> chunks = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_PATTERN.split(section.content);

        StringBuilder currentChunk = new StringBuilder();
        int chunkStart = section.start;
        int searchFrom = 0;

        for (String para : paragraphs) {
            String normalized = para.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            int relativeIndex = section.content.indexOf(normalized, searchFrom);
            if (relativeIndex < 0) {
                relativeIndex = searchFrom;
            }
            searchFrom = relativeIndex + normalized.length();
            int absoluteStart = section.start + relativeIndex;

            if (countTokens(normalized) > options.sizeTokens()) {
                flushChunkIfNeeded(chunks, currentChunk, chunkStart, section.title, options);
                chunks.addAll(splitOversizedParagraph(normalized, section.title, absoluteStart, options.sizeTokens()));
                currentChunk = new StringBuilder();
                chunkStart = absoluteStart;
                continue;
            }

            String candidate = currentChunk.length() == 0
                    ? normalized
                    : currentChunk + "\n\n" + normalized;
            if (countTokens(candidate) > options.sizeTokens() && currentChunk.length() > 0) {
                flushChunkIfNeeded(chunks, currentChunk, chunkStart, section.title, options);
                currentChunk = new StringBuilder();
            }

            if (currentChunk.length() == 0) {
                chunkStart = absoluteStart;
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(normalized);
        }

        flushChunkIfNeeded(chunks, currentChunk, chunkStart, section.title, options);

        return chunks;
    }

    /**
     * 添加块重叠
     */
    private List<ChunkSplitter> addOverlap(List<ChunkSplitter> chunks, int overlapTokens) {
        if (overlapTokens <= 0 || chunks.size() <= 1) {
            return chunks;
        }

        List<ChunkSplitter> overlapped = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            ChunkSplitter current = chunks.get(i);
            String text = current.getText();

            // 从前一个块获取重叠内容
            if (i > 0) {
                String prevText = chunks.get(i - 1).getText();
                String overlap = decodeLastTokens(prevText, overlapTokens);

                if (!overlap.isBlank() && !text.startsWith(overlap)) {
                    text = overlap + text;
                }
            }

            overlapped.add(ChunkSplitter.builder()
                    .text(text)
                    .startOffset(current.getStartOffset())
                    .endOffset(current.getEndOffset())
                    .sectionTitle(current.getSectionTitle())
                    .tokenCount(countTokens(text))
                    .build());
        }

        return overlapped;
    }

    /**
     * 创建块
     */
    private ChunkSplitter createChunk(String text, String title, int start, int end) {
        return ChunkSplitter.builder()
                .text(text)
                .startOffset(start)
                .endOffset(end)
                .sectionTitle(title)
                .tokenCount(countTokens(text))
                .build();
    }

    private void mergeOrAppend(List<ChunkSplitter> chunks, ChunkSplitter next, ChunkOptions options) {
        if (next == null || next.getText() == null || next.getText().isBlank()) {
            return;
        }
        if (!chunks.isEmpty() && next.getTokenCount() < minChunkSize) {
            ChunkSplitter previous = chunks.get(chunks.size() - 1);
            String merged = previous.getText() + "\n\n" + next.getText();
            if (countTokens(merged) <= options.sizeTokens()) {
                chunks.set(chunks.size() - 1, ChunkSplitter.builder()
                        .text(merged)
                        .startOffset(previous.getStartOffset())
                        .endOffset(next.getEndOffset())
                        .sectionTitle(previous.getSectionTitle() != null ? previous.getSectionTitle() : next.getSectionTitle())
                        .tokenCount(countTokens(merged))
                        .metadata(previous.getMetadata())
                        .build());
                return;
            }
        }
        chunks.add(next);
    }

    private void flushChunkIfNeeded(List<ChunkSplitter> chunks, StringBuilder currentChunk,
                                    int chunkStart, String title, ChunkOptions options) {
        if (currentChunk.length() == 0) {
            return;
        }
        String chunkText = currentChunk.toString().trim();
        if (chunkText.isEmpty()) {
            currentChunk.setLength(0);
            return;
        }
        mergeOrAppend(chunks, createChunk(chunkText, title, chunkStart, chunkStart + chunkText.length()), options);
        currentChunk.setLength(0);
    }

    private List<ChunkSplitter> splitOversizedParagraph(String text, String title, int absoluteStart, int sizeTokens) {
        List<ChunkSplitter> chunks = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int end = findChunkEnd(text, cursor, sizeTokens);
            String rawChunk = text.substring(cursor, end);
            String chunkText = rawChunk.trim();
            if (chunkText.isEmpty()) {
                cursor = end;
                continue;
            }
            int leadingTrim = rawChunk.indexOf(chunkText);
            int chunkStart = absoluteStart + cursor + Math.max(leadingTrim, 0);
            chunks.add(createChunk(chunkText, title, chunkStart, chunkStart + chunkText.length()));
            cursor = end;
        }
        return chunks;
    }

    private String decodeLastTokens(String text, int overlapTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (countTokens(text) <= overlapTokens) {
            return text;
        }
        int low = 0;
        int high = text.length() - 1;
        int best = high;
        while (low <= high) {
            int mid = alignBoundary(text, (low + high) / 2, false);
            String suffix = text.substring(mid);
            int tokenCount = countTokens(suffix);
            if (tokenCount <= overlapTokens) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return text.substring(best);
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return getEncoding().countTokens(text);
    }

    private Encoding getEncoding() {
        if (encoding == null) {
            synchronized (this) {
                if (encoding == null) {
                    Optional<EncodingType> encodingType = EncodingType.fromName(tokenizerEncoding.toLowerCase(Locale.ROOT));
                    encoding = Encodings.newLazyEncodingRegistry()
                            .getEncoding(encodingType.orElse(EncodingType.CL100K_BASE));
                }
            }
        }
        return encoding;
    }

    private int findChunkEnd(String text, int start, int sizeTokens) {
        int low = Math.min(text.length(), start + 1);
        int high = text.length();
        int best = low;
        while (low <= high) {
            int mid = alignBoundary(text, (low + high) / 2, true);
            if (mid <= start) {
                mid = Math.min(text.length(), start + 1);
            }
            int tokenCount = countTokens(text.substring(start, mid));
            if (tokenCount <= sizeTokens) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return snapToWordBoundary(text, start, Math.max(best, Math.min(text.length(), start + 1)), sizeTokens);
    }

    private int snapToWordBoundary(String text, int start, int currentEnd, int sizeTokens) {
        int candidate = currentEnd;
        while (candidate > start + 1) {
            char previous = text.charAt(candidate - 1);
            if (Character.isWhitespace(previous) || "，。；：,.!?、)]}".indexOf(previous) >= 0) {
                break;
            }
            candidate--;
        }
        candidate = alignBoundary(text, candidate, false);
        if (candidate <= start || candidate >= currentEnd) {
            return currentEnd;
        }
        return countTokens(text.substring(start, candidate)) <= sizeTokens ? candidate : currentEnd;
    }

    private int alignBoundary(String text, int index, boolean moveForward) {
        if (index <= 0 || index >= text.length()) {
            return Math.max(0, Math.min(index, text.length()));
        }
        if (Character.isLowSurrogate(text.charAt(index)) && Character.isHighSurrogate(text.charAt(index - 1))) {
            return moveForward ? index + 1 : index - 1;
        }
        return index;
    }

    /**
     * 段落结构
     */
    private record Section(String title, String content, int start, int end) {}

    public record ChunkOptions(int sizeTokens, int overlapTokens, boolean structuredChunk) {
    }
}
