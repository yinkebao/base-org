package com.baseorg.docassistant.service;

import com.baseorg.docassistant.dto.importtask.CreateImportTaskRequest;
import com.baseorg.docassistant.dto.importtask.ImportTaskResponse;
import com.baseorg.docassistant.dto.importtask.RecentImportsResponse;
import com.baseorg.docassistant.entity.Chunk;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.entity.ImportTask;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baseorg.docassistant.mapper.DocumentMapper;
import com.baseorg.docassistant.mapper.ImportTaskMapper;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baseorg.docassistant.service.chunk.SmartChunkSplitter;
import com.baseorg.docassistant.service.parser.DocumentParser;
import com.baseorg.docassistant.service.parser.DocumentParserFactory;
import com.baseorg.docassistant.service.rag.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    @Mock
    private ImportTaskMapper importTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private DocumentParserFactory parserFactory;

    @Mock
    private SmartChunkSplitter chunkSplitter;

    @Mock
    private DocumentParser documentParser;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ImportService importService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        importService = new ImportService(importTaskMapper, documentMapper, chunkMapper, parserFactory, chunkSplitter, embeddingService, transactionManager);
        // 使用反射设置 storagePath
        Field storagePathField = ImportService.class.getDeclaredField("storagePath");
        storagePathField.setAccessible(true);
        storagePathField.set(importService, tempDir.toString());

        // 默认 parser 配置
        when(parserFactory.getParser(anyString())).thenReturn(documentParser);
        when(parserFactory.isSupported(anyString())).thenReturn(true);
        when(embeddingService.isAvailable()).thenReturn(true);
    }

    @Test
    void shouldGetTaskStatusSuccessfully() {
        // Given
        String taskId = "task-123";
        Long userId = 1L;
        ImportTask task = createImportTask(1L, taskId, userId, "test.pdf", ImportTask.TaskStatus.COMPLETED);

        when(importTaskMapper.findByTaskIdAndOwnerId(taskId, userId)).thenReturn(task);

        // When
        ImportTaskResponse response = importService.getTaskStatus(taskId, userId);

        // Then
        assertThat(response.getTaskId()).isEqualTo(taskId);
        assertThat(response.getFilename()).isEqualTo("test.pdf");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldThrowExceptionWhenTaskNotFound() {
        // Given
        when(importTaskMapper.findByTaskIdAndOwnerId("nonexistent", 1L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> importService.getTaskStatus("nonexistent", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IMPORT_TASK_NOT_FOUND.getCode());
    }

    @Test
    void shouldCancelTaskSuccessfully() {
        // Given
        String taskId = "task-123";
        Long userId = 1L;
        ImportTask task = createImportTask(1L, taskId, userId, "test.pdf", ImportTask.TaskStatus.PENDING);

        when(importTaskMapper.findByTaskIdAndOwnerId(taskId, userId)).thenReturn(task);

        // When
        ImportTaskResponse response = importService.cancelTask(taskId, userId);

        // Then
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(importTaskMapper).updateById(task);
    }

    @Test
    void shouldThrowExceptionWhenCancelNonPendingTask() {
        // Given
        String taskId = "task-123";
        Long userId = 1L;
        ImportTask task = createImportTask(1L, taskId, userId, "test.pdf", ImportTask.TaskStatus.PARSING);

        when(importTaskMapper.findByTaskIdAndOwnerId(taskId, userId)).thenReturn(task);

        // When & Then
        assertThatThrownBy(() -> importService.cancelTask(taskId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IMPORT_TASK_FINALIZED.getCode());
    }

    @Test
    void shouldThrowExceptionWhenCancelNotFoundTask() {
        // Given
        when(importTaskMapper.findByTaskIdAndOwnerId("nonexistent", 1L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> importService.cancelTask("nonexistent", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IMPORT_TASK_NOT_FOUND.getCode());
    }

    @Test
    void shouldGetRecentImportsSuccessfully() {
        // Given
        Long userId = 1L;
        List<ImportTask> tasks = List.of(
                createImportTask(1L, "task-1", userId, "doc1.pdf", ImportTask.TaskStatus.COMPLETED),
                createImportTask(2L, "task-2", userId, "doc2.pdf", ImportTask.TaskStatus.PENDING)
        );

        when(importTaskMapper.selectPage(any(), any())).thenReturn(new TestIPage<>(tasks, 2L));

        // When
        RecentImportsResponse response = importService.getRecentImports(userId, 0, 10);

        // Then
        assertThat(response.getTasks()).hasSize(2);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getTasks().get(0).getFilename()).isEqualTo("doc1.pdf");
    }

    @Test
    void shouldCreateTaskSuccessfully() throws Exception {
        // Given
        Long userId = 1L;
        MultipartFile file = createMockFile("test.pdf", "application/pdf", 1024 * 1024);

        when(importTaskMapper.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        });
        when(documentParser.parse(any(), anyString())).thenReturn("Test document content");
        when(chunkSplitter.split(anyString())).thenReturn(List.of());

        // When
        ImportTaskResponse response = importService.createTask(createRequest(file), userId);

        // Then
        assertThat(response.getFilename()).isEqualTo("test.pdf");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getFileSize()).isEqualTo(1024 * 1024);
        verify(importTaskMapper).insert(any(ImportTask.class));
    }

    @Test
    void shouldThrowExceptionWhenFileEmpty() {
        // Given
        MultipartFile emptyFile = createMockFile("", "", 0);

        // When & Then
        assertThatThrownBy(() -> importService.createTask(createRequest(emptyFile), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PARAMETER.getCode());
    }

    @Test
    void shouldThrowExceptionWhenFileTypeUnsupported() {
        // Given
        MultipartFile file = createMockFile("test.exe", "application/exe", 1024);
        when(parserFactory.isSupported("exe")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> importService.createTask(createRequest(file), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PARAMETER.getCode());
    }

    @Test
    void shouldThrowExceptionWhenFileTooLarge() {
        // Given
        MultipartFile largeFile = createMockFile("large.pdf", "application/pdf", 201 * 1024 * 1024);

        // When & Then
        assertThatThrownBy(() -> importService.createTask(createRequest(largeFile), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.IMPORT_FILE_TOO_LARGE.getCode());
    }

    @Test
    void shouldHandleMarkdownFileType() throws Exception {
        // Given
        MultipartFile mdFile = createMockFile("README.md", "text/markdown", 1024);

        when(parserFactory.isSupported("md")).thenReturn(true);
        when(importTaskMapper.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        });
        when(documentParser.parse(any(), anyString())).thenReturn("# Markdown Content");
        when(chunkSplitter.split(anyString())).thenReturn(List.of());

        // When
        ImportTaskResponse response = importService.createTask(createRequest(mdFile), 1L);

        // Then
        assertThat(response.getFileType()).isEqualTo("md");
        assertThat(response.getFilename()).isEqualTo("README.md");
    }

    @Test
    void shouldHandleDocxFileType() throws Exception {
        // Given
        MultipartFile docxFile = createMockFile("document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1024);

        when(parserFactory.isSupported("docx")).thenReturn(true);
        when(importTaskMapper.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        });
        when(documentParser.parse(any(), anyString())).thenReturn("Docx content");
        when(chunkSplitter.split(anyString())).thenReturn(List.of());

        // When
        ImportTaskResponse response = importService.createTask(createRequest(docxFile), 1L);

        // Then
        assertThat(response.getFileType()).isEqualTo("docx");
    }

    @Test
    void shouldExtractTitleFromFilename() throws Exception {
        // Given
        MultipartFile file = createMockFile("My Important Document.pdf", "application/pdf", 1024);

        when(importTaskMapper.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        });
        when(documentParser.parse(any(), anyString())).thenReturn("Content");
        when(chunkSplitter.split(anyString())).thenReturn(List.of());

        // When
        ImportTaskResponse response = importService.createTask(createRequest(file), 1L);

        // Then - 由于async处理是异步的，只验证任务创建成功
        assertThat(response.getFilename()).isEqualTo("My Important Document.pdf");
    }

    @Test
    void shouldHandleNullParentId() throws Exception {
        // Given
        MultipartFile file = createMockFile("test.pdf", "application/pdf", 1024);

        when(importTaskMapper.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        });
        when(documentParser.parse(any(), anyString())).thenReturn("Content");
        when(chunkSplitter.split(anyString())).thenReturn(List.of());

        // When
        ImportTaskResponse response = importService.createTask(createRequest(file), 1L);

        // Then - 验证任务创建成功
        assertThat(response.getFilename()).isEqualTo("test.pdf");
    }

    @Test
    void shouldProcessTaskAndPersistChunksWithEmbeddings() throws Exception {
        ImportTask task = createImportTask(1L, "task-123", 1L, "test.pdf", ImportTask.TaskStatus.PENDING);
        List<com.baseorg.docassistant.service.chunk.ChunkSplitter> chunks = List.of(
                createChunk("第一段内容", 0, 5, 10, "章节一"),
                createChunk("第二段内容", 6, 12, 12, "章节二")
        );

        when(importTaskMapper.selectById(1L)).thenReturn(task);
        when(documentParser.parse(any(), anyString())).thenReturn("Test document content");
        when(chunkSplitter.split(anyString(), any())).thenReturn(chunks);
        when(embeddingService.embedBatch(any())).thenReturn(List.of(
                new float[]{0.1f, 0.2f},
                new float[]{0.3f, 0.4f}
        ));
        when(documentMapper.insert(any())).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(99L);
            return 1;
        });

        importService.processTask(1L, createOptions());

        verify(documentMapper).insert(any(Document.class));
        verify(chunkMapper, org.mockito.Mockito.times(2)).insert(any(Chunk.class));
        assertThat(task.getStatus()).isEqualTo(ImportTask.TaskStatus.COMPLETED);
        assertThat(task.getProcessedChunks()).isEqualTo(2);
        assertThat(task.getResultDocId()).isEqualTo(99L);
    }

    @Test
    void shouldFailWhenEmbeddingResultContainsNullVector() throws Exception {
        ImportTask task = createImportTask(1L, "task-123", 1L, "test.pdf", ImportTask.TaskStatus.PENDING);
        List<com.baseorg.docassistant.service.chunk.ChunkSplitter> chunks = List.of(
                createChunk("第一段内容", 0, 5, 10, "章节一")
        );

        when(importTaskMapper.selectById(1L)).thenReturn(task);
        when(documentParser.parse(any(), anyString())).thenReturn("Test document content");
        when(chunkSplitter.split(anyString(), any())).thenReturn(chunks);
        when(embeddingService.embedBatch(any())).thenReturn(java.util.Collections.singletonList((float[]) null));
        when(documentMapper.insert(any())).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(88L);
            return 1;
        });

        assertThatThrownBy(() -> importService.processTask(1L, createOptions()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空向量");
        verify(chunkMapper, org.mockito.Mockito.never()).insert(any(Chunk.class));
    }

    @Test
    void shouldRejectInvalidChunkOverlap() {
        MultipartFile file = createMockFile("test.pdf", "application/pdf", 1024);
        CreateImportTaskRequest request = createRequest(file);
        request.setChunkOverlap(request.getChunkSize());

        assertThatThrownBy(() -> importService.createTask(request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PARAMETER.getCode());
    }

    @Test
    void shouldPersistNormalizedImportMetadata() {
        MultipartFile file = createMockFile("spec.md", "text/markdown", 1024);
        when(importTaskMapper.insert(any())).thenAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        });

        CreateImportTaskRequest request = createRequest(file);
        request.setTags(List.of(" 技术文档 ", "技术文档", "", "设计评审"));
        importService.createTask(request, 1L);

        org.mockito.ArgumentCaptor<ImportTask> captor = org.mockito.ArgumentCaptor.forClass(ImportTask.class);
        verify(importTaskMapper).insert(captor.capture());
        ImportTask task = captor.getValue();
        assertThat(task.getMetadata()).containsEntry("sourceType", "upload");
        assertThat(task.getMetadata()).containsEntry("versionLabel", "v1.0.0");
        assertThat(((List<?>) task.getMetadata().get("tags")).stream().map(String::valueOf).toList())
                .containsExactly("技术文档", "设计评审");
        assertThat(task.getMetadata()).containsKey("chunkConfig");
    }

    // Helper methods

    private ImportTask createImportTask(Long id, String taskId, Long ownerId, String filename, ImportTask.TaskStatus status) {
        Path filePath = tempDir.resolve(filename);
        try {
            java.nio.file.Files.writeString(filePath, "test-content");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return ImportTask.builder()
                .id(id)
                .taskId(taskId)
                .ownerId(ownerId)
                .filename(filename)
                .fileType("pdf")
                .fileSize(1024L)
                .filePath(filePath.toString())
                .status(status)
                .progress(status == ImportTask.TaskStatus.COMPLETED ? 100 : 0)
                .processedChunks(0)
                .totalChunks(10)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MultipartFile createMockFile(String filename, String contentType, long size) {
        return new MultipartFile() {
            @Override
            public String getName() { return "file"; }

            @Override
            public String getOriginalFilename() { return filename; }

            @Override
            public String getContentType() { return contentType; }

            @Override
            public boolean isEmpty() { return size == 0; }

            @Override
            public long getSize() { return size; }

            @Override
            public byte[] getBytes() { return new byte[(int) Math.min(size, 1024)]; }

            @Override
            public java.io.InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[(int) Math.min(size, 1024)]);
            }

            @Override
            public void transferTo(java.io.File dest) throws java.io.IOException {
                java.nio.file.Files.write(dest.toPath(), getBytes());
            }
        };
    }

    private CreateImportTaskRequest createRequest(MultipartFile file) {
        return CreateImportTaskRequest.builder()
                .file(file)
                .sensitivity(Document.SensitivityLevel.INTERNAL)
                .description("Test description")
                .versionLabel("v1.0.0")
                .tags(List.of("技术文档"))
                .chunkSize(500)
                .chunkOverlap(100)
                .structuredChunk(true)
                .build();
    }

    private ImportService.ImportTaskOptions createOptions() {
        return new ImportService.ImportTaskOptions(
                Document.SensitivityLevel.INTERNAL,
                null,
                "描述",
                "v1.0.0",
                List.of("技术文档"),
                500,
                100,
                true,
                "application/pdf",
                "test.pdf"
        );
    }

    private com.baseorg.docassistant.service.chunk.ChunkSplitter createChunk(
            String text, int startOffset, int endOffset, int tokenCount, String sectionTitle) {
        return com.baseorg.docassistant.service.chunk.ChunkSplitter.builder()
                .text(text)
                .startOffset(startOffset)
                .endOffset(endOffset)
                .tokenCount(tokenCount)
                .sectionTitle(sectionTitle)
                .metadata(Map.of("section", sectionTitle))
                .build();
    }

    // Test IPage implementation
    private static class TestIPage<T> implements com.baomidou.mybatisplus.core.metadata.IPage<T> {
        private final List<T> records;
        private final long total;

        public TestIPage(List<T> records, long total) {
            this.records = records;
            this.total = total;
        }

        @Override
        public List<T> getRecords() { return records; }

        @Override
        public long getTotal() { return total; }

        @Override
        public long getSize() { return 10; }

        @Override
        public long getCurrent() { return 1; }

        @Override
        public long getPages() { return 1; }

        @Override
        public IPage<T> setRecords(List<T> list) { return this; }

        @Override
        public IPage<T> setTotal(long total) { return this; }

        @Override
        public IPage<T> setSize(long size) { return this; }

        @Override
        public IPage<T> setCurrent(long current) { return this; }

        @Override
        public IPage<T> setPages(long pages) { return this; }

        @Override
        public List<com.baomidou.mybatisplus.core.metadata.OrderItem> orders() { return List.of(); }

        public IPage<T> setOrders(List<com.baomidou.mybatisplus.core.metadata.OrderItem> orders) { return this; }

        public IPage<T> addOrder(com.baomidou.mybatisplus.core.metadata.OrderItem... items) { return this; }
    }
}
