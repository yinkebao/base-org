package com.baseorg.docassistant.controller;

import com.baseorg.docassistant.dto.ApiResponse;
import com.baseorg.docassistant.dto.document.*;
import com.baseorg.docassistant.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理控制器
 */
@Tag(name = "文档管理", description = "文档查询与管理接口")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 获取文档树
     */
    @Operation(summary = "获取文档树", description = "获取用户的文档目录树结构")
    @GetMapping("/tree")
    public ApiResponse<DocumentTreeNode> getDocumentTree(
            @Parameter(description = "父目录ID") @RequestParam(required = false) Long parentId,
            @AuthenticationPrincipal Long userId) {

        DocumentTreeNode tree = documentService.getDocumentTree(userId, parentId);
        return ApiResponse.success(tree);
    }

    /**
     * 获取文档列表
     */
    @Operation(summary = "获取文档列表", description = "分页获取文档列表，支持筛选")
    @GetMapping
    public ApiResponse<DocumentListResponse> getDocumentList(
            @Parameter(description = "父目录ID") @RequestParam(required = false) Long parentId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Long userId) {

        DocumentListResponse response = documentService.getDocumentList(
                userId, parentId, page, size, status, keyword);
        return ApiResponse.success(response);
    }

    /**
     * 获取文档详情
     */
    @Operation(summary = "获取文档详情", description = "根据ID获取文档详细信息")
    @GetMapping("/{docId}")
    public ApiResponse<DocumentDetailResponse> getDocumentDetail(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @AuthenticationPrincipal Long userId) {

        DocumentDetailResponse response = documentService.getDocumentDetail(docId, userId);
        return ApiResponse.success(response);
    }

    /**
     * 获取面包屑导航
     */
    @Operation(summary = "获取面包屑", description = "获取文档的面包屑导航路径")
    @GetMapping("/{docId}/breadcrumb")
    public ApiResponse<BreadcrumbResponse> getBreadcrumb(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @AuthenticationPrincipal Long userId) {

        BreadcrumbResponse response = documentService.getBreadcrumb(docId, userId);
        return ApiResponse.success(response);
    }

    /**
     * 更新文档
     */
    @Operation(summary = "更新文档", description = "更新文档标题与 Markdown 正文")
    @PutMapping("/{docId}")
    public ApiResponse<DocumentDetailResponse> updateDocument(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal Long userId) {

        DocumentDetailResponse response = documentService.updateDocument(docId, userId, request);
        return ApiResponse.success(response);
    }

    /**
     * 上传文档图片
     */
    @Operation(summary = "上传文档图片", description = "上传富文本编辑器图片资源")
    @PostMapping("/{docId}/assets/images")
    public ApiResponse<DocumentImageUploadResponse> uploadDocumentImage(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "alt", required = false) String alt,
            @AuthenticationPrincipal Long userId) {

        DocumentImageUploadResponse response = documentService.uploadDocumentImage(docId, userId, image, alt);
        return ApiResponse.success(response);
    }

    /**
     * 访问文档图片
     */
    @Operation(summary = "获取文档图片", description = "返回已上传的文档图片资源")
    @GetMapping("/{docId}/assets/images/{filename:.+}")
    public ResponseEntity<Resource> getDocumentImage(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @Parameter(description = "文件名") @PathVariable String filename,
            @AuthenticationPrincipal Long userId) {

        DocumentImageResource imageResource = documentService.loadDocumentImage(docId, userId, filename);
        return ResponseEntity.ok()
                .contentType(imageResource.getMediaType())
                .body(imageResource.getResource());
    }
}
