package com.baseorg.docassistant.service;

import com.baseorg.docassistant.dto.document.*;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    private DocumentService documentService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        documentService = new DocumentService(documentMapper, chunkMapper);
        // 使用反射设置 storagePath
        Field storagePathField = DocumentService.class.getDeclaredField("storagePath");
        storagePathField.setAccessible(true);
        storagePathField.set(documentService, tempDir.toString());
    }

    @Test
    void shouldGetDocumentTreeSuccessfully() {
        // Given
        Long userId = 1L;
        List<Document> documents = List.of(
                createDocument(1L, "Folder1", null, null, userId),
                createDocument(2L, "Child1.doc", "docx", 1L, userId),
                createDocument(3L, "Child2.doc", "pdf", 1L, userId),
                createDocument(4L, "RootDoc.doc", "md", null, userId)
        );

        when(documentMapper.findByOwnerId(userId)).thenReturn(documents);

        // When
        DocumentTreeNode tree = documentService.getDocumentTree(userId, null);

        // Then
        assertThat(tree.getId()).isEqualTo(0L);
        assertThat(tree.isFolder()).isTrue();
        assertThat(tree.getChildren()).hasSize(2); // Folder1 and RootDoc

        DocumentTreeNode folder1 = tree.getChildren().get(0);
        assertThat(folder1.getTitle()).isEqualTo("Folder1");
        assertThat(folder1.getChildren()).hasSize(2);

        DocumentTreeNode rootDoc = tree.getChildren().get(1);
        assertThat(rootDoc.getTitle()).isEqualTo("RootDoc.doc");
    }

    @Test
    void shouldGetDocumentListSuccessfully() {
        // Given
        Long userId = 1L;
        Document doc1 = createDocument(1L, "Doc1", "pdf", null, userId);
        Document doc2 = createDocument(2L, "Doc2", "docx", null, userId);

        when(documentMapper.selectPage(any(), any())).thenReturn(new TestIPage<>(List.of(doc1, doc2), 2L));

        // When
        DocumentListResponse response = documentService.getDocumentList(userId, null, 0, 10, null, null);

        // Then
        assertThat(response.getDocuments()).hasSize(2);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getDocuments().get(0).getTitle()).isEqualTo("Doc1");
    }

    @Test
    void shouldGetDocumentListWithKeywordFilter() {
        // Given
        Long userId = 1L;
        Document doc = createDocument(1L, "重要文档", "pdf", null, userId);

        when(documentMapper.selectPage(any(), any())).thenReturn(new TestIPage<>(List.of(doc), 1L));

        // When
        DocumentListResponse response = documentService.getDocumentList(userId, null, 0, 10, null, "重要");

        // Then
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).getTitle()).isEqualTo("重要文档");
    }

    @Test
    void shouldGetDocumentDetailSuccessfully() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Test Doc", "pdf", null, userId);

        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);
        when(chunkMapper.countByDocId(docId)).thenReturn(5);

        // When
        DocumentDetailResponse response = documentService.getDocumentDetail(docId, userId);

        // Then
        assertThat(response.getId()).isEqualTo(docId);
        assertThat(response.getTitle()).isEqualTo("Test Doc");
        assertThat(response.getChunkCount()).isEqualTo(5);
    }

    @Test
    void shouldThrowExceptionWhenDocumentNotFound() {
        // Given
        when(documentMapper.findByIdAndOwnerId(1L, 1L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> documentService.getDocumentDetail(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DOC_NOT_FOUND.getCode());
    }

    @Test
    void shouldGetBreadcrumbSuccessfully() {
        // Given
        Long userId = 1L;
        Document parent = createDocument(2L, "Parent", null, null, userId);
        Document child = createDocument(1L, "Child", "pdf", 2L, userId);

        when(documentMapper.findByIdAndOwnerId(1L, userId)).thenReturn(child);
        when(documentMapper.findByIdAndOwnerId(2L, userId)).thenReturn(parent);

        // When
        BreadcrumbResponse response = documentService.getBreadcrumb(1L, userId);

        // Then
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("Parent");
        assertThat(response.getItems().get(0).getType()).isEqualTo("folder");
        assertThat(response.getItems().get(1).getTitle()).isEqualTo("Child");
        assertThat(response.getItems().get(1).getType()).isEqualTo("file");
    }

    @Test
    void shouldUpdateDocumentSuccessfully() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Old Title", "pdf", null, userId);
        doc.setVersion(1);

        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setTitle("New Title");
        request.setContentMarkdown("New content");
        request.setVersion(1);

        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);
        when(documentMapper.selectById(docId)).thenReturn(doc);
        when(chunkMapper.countByDocId(docId)).thenReturn(0);

        // When
        DocumentDetailResponse response = documentService.updateDocument(docId, userId, request);

        // Then
        verify(documentMapper).updateById(doc);
        assertThat(doc.getTitle()).isEqualTo("New Title");
        assertThat(doc.getContentMarkdown()).isEqualTo("New content");
        assertThat(doc.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldThrowExceptionWhenVersionConflict() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Title", "pdf", null, userId);
        doc.setVersion(2);

        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setVersion(1); // 不同版本

        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // When & Then
        assertThatThrownBy(() -> documentService.updateDocument(docId, userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DOC_VERSION_CONFLICT.getCode());
    }

    @Test
    void shouldUploadDocumentImageSuccessfully() throws Exception {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Doc", "md", null, userId);

        MultipartFile image = createMockImage("test.png", "image/png", 1024);

        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // When
        DocumentImageUploadResponse response = documentService.uploadDocumentImage(docId, userId, image, "Alt text");

        // Then
        assertThat(response.getUrl()).contains("/api/v1/documents/" + docId + "/assets/images/");
        assertThat(response.getAlt()).isEqualTo("Alt text");
        assertThat(response.getFilename()).endsWith(".png");
        assertThat(response.getSize()).isEqualTo(1024);
    }

    @Test
    void shouldThrowExceptionWhenImageTypeInvalid() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Doc", "md", null, userId);

        MultipartFile invalidImage = createMockImage("test.bmp", "image/bmp", 1024);
        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // When & Then
        assertThatThrownBy(() -> documentService.uploadDocumentImage(docId, userId, invalidImage, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DOC_IMAGE_INVALID_TYPE.getCode());
    }

    @Test
    void shouldThrowExceptionWhenImageTooLarge() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Doc", "md", null, userId);

        MultipartFile largeImage = createMockImage("large.png", "image/png", 20 * 1024 * 1024);
        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // When & Then
        assertThatThrownBy(() -> documentService.uploadDocumentImage(docId, userId, largeImage, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DOC_IMAGE_TOO_LARGE.getCode());
    }

    @Test
    void shouldLoadDocumentImageSuccessfully() throws Exception {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        String filename = "test_image.png";

        Document doc = createDocument(docId, "Doc", "md", null, userId);
        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // 创建测试图片文件
        Path imageDir = tempDir.resolve("document-assets").resolve(String.valueOf(userId)).resolve(String.valueOf(docId));
        java.nio.file.Files.createDirectories(imageDir);
        Path imageFile = imageDir.resolve(filename);
        java.nio.file.Files.write(imageFile, "fake image content".getBytes());

        // When
        DocumentImageResource response = documentService.loadDocumentImage(docId, userId, filename);

        // Then
        assertThat(response.getResource()).isNotNull();
        assertThat(response.getMediaType()).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenImageNotFound() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Doc", "md", null, userId);
        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // When & Then
        assertThatThrownBy(() -> documentService.loadDocumentImage(docId, userId, "nonexistent.png"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void shouldThrowExceptionWhenImageEscapesDirectory() {
        // Given
        Long docId = 1L;
        Long userId = 1L;
        Document doc = createDocument(docId, "Doc", "md", null, userId);
        when(documentMapper.findByIdAndOwnerId(docId, userId)).thenReturn(doc);

        // When & Then - 路径遍历攻击
        assertThatThrownBy(() -> documentService.loadDocumentImage(docId, userId, "../../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    // Helper methods

    private Document createDocument(Long id, String title, String fileType, Long parentId, Long ownerId) {
        return Document.builder()
                .id(id)
                .title(title)
                .fileType(fileType)
                .parentId(parentId)
                .ownerId(ownerId)
                .status(Document.DocumentStatus.DRAFT)
                .sensitivity(Document.SensitivityLevel.INTERNAL)
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private MultipartFile createMockImage(String filename, String contentType, long size) {
        return new MultipartFile() {
            @Override
            public String getName() { return "file"; }

            @Override
            public String getOriginalFilename() { return filename; }

            @Override
            public String getContentType() { return contentType; }

            @Override
            public boolean isEmpty() { return false; }

            @Override
            public long getSize() { return size; }

            @Override
            public byte[] getBytes() { return new byte[(int) size]; }

            @Override
            public java.io.InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[(int) size]);
            }

            @Override
            public void transferTo(java.io.File dest) {
                throw new UnsupportedOperationException();
            }
        };
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
