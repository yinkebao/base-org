package com.baseorg.docassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baseorg.docassistant.dto.document.*;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L;

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;

    @Value("${app.storage.local.path}")
    private String storagePath;

    /**
     * 获取文档树
     */
    @Transactional(readOnly = true)
    public DocumentTreeNode getDocumentTree(Long userId, Long parentId) {
        log.debug("获取文档树: userId={}, parentId={}", userId, parentId);

        List<Document> allDocs = documentMapper.findByOwnerId(userId);
        return buildTree(allDocs, parentId);
    }

    /**
     * 获取文档列表
     */
    @Transactional(readOnly = true)
    public DocumentListResponse getDocumentList(Long userId, Long parentId,
                                                 int page, int size,
                                                 String status, String keyword) {
        log.debug("获取文档列表: userId={}, parentId={}, page={}", userId, parentId, page);

        Page<Document> pageParam = new Page<>(page + 1, size);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Document::getOwnerId, userId);

        if (parentId != null) {
            wrapper.eq(Document::getParentId, parentId);
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Document::getTitle, keyword);
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Document::getStatus, Document.DocumentStatus.valueOf(status));
        }

        wrapper.orderByDesc(Document::getCreatedAt);

        IPage<Document> docPage = documentMapper.selectPage(pageParam, wrapper);

        List<DocumentListResponse.DocumentListItem> items = docPage.getRecords().stream()
                .map(this::toListResponse)
                .toList();

        return DocumentListResponse.builder()
                .documents(items)
                .total(docPage.getTotal())
                .page(page)
                .size(size)
                .build();
    }

    /**
     * 获取文档详情
     */
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocumentDetail(Long docId, Long userId) {
        log.debug("获取文档详情: docId={}, userId={}", docId, userId);

        Document document = documentMapper.findByIdAndOwnerId(docId, userId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }

        DocumentDetailResponse response = DocumentDetailResponse.fromEntity(document);
        response.setChunkCount(chunkMapper.countByDocId(docId));

        return response;
    }

    /**
     * 获取面包屑导航
     */
    @Transactional(readOnly = true)
    public BreadcrumbResponse getBreadcrumb(Long docId, Long userId) {
        log.debug("获取面包屑: docId={}, userId={}", docId, userId);

        List<BreadcrumbResponse.BreadcrumbItem> items = new ArrayList<>();

        Document document = documentMapper.findByIdAndOwnerId(docId, userId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }

        if (document.getParentId() != null) {
            List<Document> ancestors = getAncestors(document.getParentId(), userId);
            Collections.reverse(ancestors);

            for (Document ancestor : ancestors) {
                items.add(BreadcrumbResponse.BreadcrumbItem.builder()
                        .id(ancestor.getId())
                        .title(ancestor.getTitle())
                        .type(ancestor.getFileType() == null ? "folder" : "file")
                        .build());
            }
        }

        items.add(BreadcrumbResponse.BreadcrumbItem.builder()
                .id(document.getId())
                .title(document.getTitle())
                .type(document.getFileType() == null ? "folder" : "file")
                .build());

        return BreadcrumbResponse.builder().items(items).build();
    }

    /**
     * 更新文档
     */
    @Transactional
    public DocumentDetailResponse updateDocument(Long docId, Long userId, UpdateDocumentRequest request) {
        log.debug("更新文档: docId={}, userId={}", docId, userId);

        Document document = getOwnedDocument(docId, userId);

        if (request.getVersion() != null && document.getVersion() != null
                && !Objects.equals(request.getVersion(), document.getVersion())) {
            throw new BusinessException(ErrorCode.DOC_VERSION_CONFLICT);
        }

        String nextTitle = Optional.ofNullable(request.getTitle())
                .orElse(Optional.ofNullable(document.getTitle()).orElse(""))
                .trim();
        if (nextTitle.isBlank()) {
            nextTitle = Optional.ofNullable(document.getTitle()).orElse("未命名文档");
        }

        document.setTitle(nextTitle);
        document.setContentMarkdown(Optional.ofNullable(request.getContentMarkdown()).orElse(""));
        document.setUpdatedAt(LocalDateTime.now());
        document.setVersion((document.getVersion() == null ? 0 : document.getVersion()) + 1);

        documentMapper.updateById(document);
        return getDocumentDetail(docId, userId);
    }

    /**
     * 上传文档图片
     */
    @Transactional(readOnly = true)
    public DocumentImageUploadResponse uploadDocumentImage(Long docId, Long userId, MultipartFile image, String alt) {
        log.debug("上传文档图片: docId={}, userId={}, filename={}", docId, userId, image != null ? image.getOriginalFilename() : null);

        getOwnedDocument(docId, userId);
        validateImage(image);

        String originalName = Optional.ofNullable(image.getOriginalFilename()).orElse("image");
        String filename = buildAssetFilename(originalName);
        Path targetDir = Paths.get(storagePath, "document-assets", String.valueOf(userId), String.valueOf(docId));

        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(filename).normalize();
            image.transferTo(targetFile);

            return DocumentImageUploadResponse.builder()
                    .url(String.format("/api/v1/documents/%d/assets/images/%s", docId, filename))
                    .alt(String.valueOf(alt == null ? "" : alt).trim())
                    .filename(filename)
                    .size(image.getSize())
                    .build();
        } catch (Exception ex) {
            log.error("上传文档图片失败: docId={}, userId={}", docId, userId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败");
        }
    }

    /**
     * 加载文档图片资源
     */
    @Transactional(readOnly = true)
    public DocumentImageResource loadDocumentImage(Long docId, Long userId, String filename) {
        getOwnedDocument(docId, userId);

        Path targetDir = Paths.get(storagePath, "document-assets", String.valueOf(userId), String.valueOf(docId)).normalize();
        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(targetDir) || !Files.exists(targetFile)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片资源不存在");
        }

        try {
            Resource resource = new UrlResource(targetFile.toUri());
            String contentType = Files.probeContentType(targetFile);
            MediaType mediaType = MediaType.parseMediaType(
                    contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE
            );
            return DocumentImageResource.builder()
                    .resource(resource)
                    .mediaType(mediaType)
                    .build();
        } catch (MalformedURLException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片资源不存在");
        } catch (Exception ex) {
            log.error("读取文档图片失败: docId={}, userId={}, filename={}", docId, userId, filename, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片读取失败");
        }
    }

    /**
     * 构建文档树
     */
    private DocumentTreeNode buildTree(List<Document> allDocs, Long rootParentId) {
        Map<Long, List<Document>> childrenMap = new HashMap<>();
        allDocs.forEach(doc -> {
            Long key = doc.getParentId() != null ? doc.getParentId() : 0L;
            childrenMap.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
        });

        Long rootKey = rootParentId != null ? rootParentId : 0L;
        List<Document> rootDocs = childrenMap.getOrDefault(rootKey, List.of());

        List<DocumentTreeNode> nodes = rootDocs.stream()
                .map(doc -> buildNode(doc, childrenMap))
                .toList();

        return DocumentTreeNode.builder()
                .id(rootKey)
                .title("文档")
                .isFolder(true)
                .children(nodes)
                .build();
    }

    private DocumentTreeNode buildNode(Document doc, Map<Long, List<Document>> childrenMap) {
        List<DocumentTreeNode> children = childrenMap.getOrDefault(doc.getId(), List.of()).stream()
                .map(d -> buildNode(d, childrenMap))
                .toList();
        return DocumentTreeNode.fromEntity(doc, children);
    }

    private List<Document> getAncestors(Long parentId, Long userId) {
        List<Document> ancestors = new ArrayList<>();
        Long currentId = parentId;
        Set<Long> visited = new HashSet<>();

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            Document parent = documentMapper.findByIdAndOwnerId(currentId, userId);
            if (parent == null) break;
            ancestors.add(parent);
            currentId = parent.getParentId();
        }

        return ancestors;
    }

    private Document getOwnedDocument(Long docId, Long userId) {
        Document document = documentMapper.findByIdAndOwnerId(docId, userId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOC_NOT_FOUND);
        }
        return document;
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择图片文件");
        }
        String contentType = Optional.ofNullable(image.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.DOC_IMAGE_INVALID_TYPE);
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.DOC_IMAGE_TOO_LARGE);
        }
    }

    private String buildAssetFilename(String originalName) {
        String sanitized = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        int dotIndex = sanitized.lastIndexOf('.');
        String extension = dotIndex >= 0 ? sanitized.substring(dotIndex) : ".png";
        return System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private DocumentListResponse.DocumentListItem toListResponse(Document doc) {
        return DocumentListResponse.DocumentListItem.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .fileType(doc.getFileType())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .sensitivity(doc.getSensitivity() != null ? doc.getSensitivity().name() : null)
                .parentId(doc.getParentId())
                .fileSize(doc.getFileSize())
                .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                .updatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
                .build();
    }
}
