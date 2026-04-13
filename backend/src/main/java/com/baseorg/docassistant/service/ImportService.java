package com.baseorg.docassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baseorg.docassistant.dto.importtask.CreateImportTaskRequest;
import com.baseorg.docassistant.dto.importtask.ImportTaskResponse;
import com.baseorg.docassistant.dto.importtask.RecentImportsResponse;
import com.baseorg.docassistant.entity.Chunk;
import com.baseorg.docassistant.entity.Document;
import com.baseorg.docassistant.entity.ImportTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baseorg.docassistant.exception.BusinessException;
import com.baseorg.docassistant.exception.ErrorCode;
import com.baseorg.docassistant.mapper.ChunkMapper;
import com.baseorg.docassistant.mapper.DocumentMapper;
import com.baseorg.docassistant.mapper.ImportTaskMapper;
import com.baseorg.docassistant.service.chunk.ChunkSplitter;
import com.baseorg.docassistant.service.chunk.SmartChunkSplitter;
import com.baseorg.docassistant.service.parser.DocumentParser;
import com.baseorg.docassistant.service.parser.DocumentParserFactory;
import com.baseorg.docassistant.service.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文档导入服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final int EMBEDDING_BATCH_SIZE = 16;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ImportTaskMapper importTaskMapper;
    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final DocumentParserFactory parserFactory;
    private final SmartChunkSplitter chunkSplitter;
    private final EmbeddingService embeddingService;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.storage.local.path:${user.home}/docassist/storage}")
    private String storagePath;

    /**
     * 创建导入任务
     */
    @Transactional
    public ImportTaskResponse createTask(CreateImportTaskRequest request, Long userId) {
        validateRequest(request);
        ImportTaskOptions options = ImportTaskOptions.from(request);
        MultipartFile file = request.getFile();

        String fileType = getFileType(file.getOriginalFilename());
        String filePath = saveFile(file, userId);

        ImportTask task = ImportTask.builder()
                .taskId(UUID.randomUUID().toString())
                .ownerId(userId)
                .filename(file.getOriginalFilename())
                .fileType(fileType)
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(ImportTask.TaskStatus.PENDING)
                .progress(0)
                .processedChunks(0)
                .metadata(options.toMetadata())
                .build();

        importTaskMapper.insert(task);

        log.info("创建导入任务: taskId={}, filename={}, userId={}",
                task.getTaskId(), task.getFilename(), userId);

        ImportService proxy = currentProxy();
        if (proxy == this) {
            log.debug("ImportService 未通过 Spring 代理调用，跳过异步处理触发: taskId={}", task.getTaskId());
        } else {
            proxy.processTaskAsync(task.getId(), options);
        }

        return ImportTaskResponse.fromEntity(task);
    }

    /**
     * 获取任务状态
     */
    @Transactional(readOnly = true)
    public ImportTaskResponse getTaskStatus(String taskId, Long userId) {
        ImportTask task = importTaskMapper.findByTaskIdAndOwnerId(taskId, userId);
        if (task == null) {
            throw new BusinessException(ErrorCode.IMPORT_TASK_NOT_FOUND);
        }
        return ImportTaskResponse.fromEntity(task);
    }

    /**
     * 取消任务
     */
    @Transactional
    public ImportTaskResponse cancelTask(String taskId, Long userId) {
        ImportTask task = importTaskMapper.findByTaskIdAndOwnerId(taskId, userId);
        if (task == null) {
            throw new BusinessException(ErrorCode.IMPORT_TASK_NOT_FOUND);
        }

        if (task.getStatus() != ImportTask.TaskStatus.PENDING) {
            throw new BusinessException(ErrorCode.IMPORT_TASK_FINALIZED, "任务已在处理中或已完成，无法取消");
        }

        task.setStatus(ImportTask.TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);

        log.info("取消导入任务: taskId={}", taskId);

        return ImportTaskResponse.fromEntity(task);
    }

    /**
     * 获取最近导入任务
     */
    @Transactional(readOnly = true)
    public RecentImportsResponse getRecentImports(Long userId, int page, int size) {
        Page<ImportTask> pageParam = new Page<>(page + 1, size);

        LambdaQueryWrapper<ImportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImportTask::getOwnerId, userId)
               .orderByDesc(ImportTask::getCreatedAt);

        IPage<ImportTask> taskPage = importTaskMapper.selectPage(pageParam, wrapper);

        List<ImportTaskResponse> tasks = taskPage.getRecords().stream()
                .map(ImportTaskResponse::fromEntity)
                .toList();

        return RecentImportsResponse.builder()
                .tasks(tasks)
                .total(taskPage.getTotal())
                .page(page)
                .size(size)
                .build();
    }

    @Async
    public void processTaskAsync(Long taskId, ImportTaskOptions options) {
        try {
            executeInTransaction(() -> {
                try {
                    processTask(taskId, options);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException("导入任务事务执行失败", ex);
                }
            });
        } catch (Exception e) {
            Throwable failure = unwrapFailure(e);
            log.error("导入任务处理失败: taskId={}", taskId, failure);
            executeInTransaction(() -> markTaskFailed(taskId, failure.getMessage()));
        }
    }

    @Transactional
    public void processTask(Long taskId, ImportTaskOptions options) throws Exception {
        ImportTask task = importTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.IMPORT_TASK_NOT_FOUND);
        }

        updateTaskStatus(task, ImportTask.TaskStatus.PARSING, 10);
        String content = parseDocument(task);

        updateTaskStatus(task, ImportTask.TaskStatus.CHUNKING, 30);
        List<ChunkSplitter> chunks = chunkSplitter.split(content, options.chunkOptions());
        task.setTotalChunks(chunks.size());
        task.setProcessedChunks(0);
        importTaskMapper.updateById(task);

        Document document = createDocument(task, options);
        task.setResultDocId(document.getId());

        updateTaskStatus(task, ImportTask.TaskStatus.EMBEDDING, 50);
        List<Chunk> chunkEntities = buildChunks(document.getId(), chunks, task);

        updateTaskStatus(task, ImportTask.TaskStatus.STORING, 80);
        persistChunks(chunkEntities);

        task.setStatus(ImportTask.TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setCompletedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);

        log.info("导入任务完成: taskId={}, docId={}, chunks={}",
                task.getTaskId(), document.getId(), chunks.size());
    }

    private String parseDocument(ImportTask task) throws Exception {
        DocumentParser parser = parserFactory.getParser(task.getFileType());
        if (parser == null) {
            throw new BusinessException(ErrorCode.IMPORT_PARSE_ERROR,
                    "不支持的文件类型: " + task.getFileType());
        }

        Path filePath = Paths.get(task.getFilePath());
        String content = parser.parse(Files.newInputStream(filePath), task.getFilename());

        log.debug("文档解析完成: taskId={}, contentLength={}", task.getTaskId(), content.length());
        return content;
    }

    private Document createDocument(ImportTask task, ImportTaskOptions options) {
        Document document = Document.builder()
                .title(extractTitle(task.getFilename()))
                .description(options.description())
                .filePath(task.getFilePath())
                .fileType(task.getFileType())
                .fileSize(task.getFileSize())
                .status(Document.DocumentStatus.DRAFT)
                .sensitivity(options.sensitivity())
                .ownerId(task.getOwnerId())
                .parentId(options.parentId())
                .version(1)
                .metadata(options.toDocumentMetadataJson())
                .build();

        documentMapper.insert(document);
        return document;
    }

    private List<Chunk> buildChunks(Long documentId, List<ChunkSplitter> chunks, ImportTask task) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        if (!embeddingService.isAvailable()) {
            throw new IllegalStateException("EmbeddingModel 未配置，无法为导入文档生成向量");
        }

        List<Chunk> entities = new ArrayList<>(chunks.size());
        int processed = 0;

        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
            List<ChunkSplitter> batch = chunks.subList(start, end);
            List<float[]> embeddings = embeddingService.embedBatch(batch.stream()
                    .map(ChunkSplitter::getText)
                    .toList());

            if (embeddings == null) {
                throw new IllegalStateException("批量向量化返回为空");
            }
            if (embeddings.size() != batch.size()) {
                throw new IllegalStateException("批量向量化结果数量与分块数量不一致");
            }

            for (int i = 0; i < batch.size(); i++) {
                float[] embedding = embeddings.get(i);
                if (embedding == null || embedding.length == 0) {
                    throw new IllegalStateException("存在空向量，导入任务已中止");
                }

                ChunkSplitter chunk = batch.get(i);
                entities.add(Chunk.builder()
                        .docId(documentId)
                        .chunkIndex(start + i)
                        .text(chunk.getText())
                        .embedding(arrayToVectorString(embedding))
                        .tokens(chunk.getTokenCount())
                        .metadata(chunk.getMetadata() == null ? Map.of() : chunk.getMetadata())
                        .startOffset(chunk.getStartOffset())
                        .endOffset(chunk.getEndOffset())
                        .sectionTitle(chunk.getSectionTitle())
                        .build());
            }

            processed += batch.size();
            task.setProcessedChunks(processed);
            task.setProgress(calculateEmbeddingProgress(processed, chunks.size()));
            importTaskMapper.updateById(task);
        }

        return entities;
    }

    private void persistChunks(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            chunkMapper.insert(chunk);
        }
    }

    private void updateTaskStatus(ImportTask task, ImportTask.TaskStatus status, int progress) {
        task.setStatus(status);
        task.setProgress(progress);
        importTaskMapper.updateById(task);
        log.debug("任务状态更新: taskId={}, status={}, progress={}%", task.getTaskId(), status, progress);
    }

    @Transactional
    public void markTaskFailed(Long taskId, String errorMessage) {
        ImportTask task = importTaskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(ImportTask.TaskStatus.FAILED);
            task.setErrorMessage(errorMessage);
            task.setCompletedAt(LocalDateTime.now());
            importTaskMapper.updateById(task);
        }
    }

    private void validateRequest(CreateImportTaskRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "导入请求不能为空");
        }
        validateFile(request.getFile());
        if (request.getSensitivity() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请选择敏感等级");
        }
        if (request.getVersionLabel() == null || request.getVersionLabel().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "版本号不能为空");
        }
        if (request.getChunkSize() == null || request.getChunkSize() < 100 || request.getChunkSize() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Chunk 大小需在 100-2000 之间");
        }
        if (request.getChunkOverlap() == null || request.getChunkOverlap() < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "重叠 token 不能为负数");
        }
        if (request.getChunkOverlap() >= request.getChunkSize()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "重叠 token 必须小于 chunk 大小");
        }
        if (request.getStructuredChunk() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "结构化分块配置不能为空");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文件不能为空");
        }

        String fileType = getFileType(file.getOriginalFilename());
        if (!parserFactory.isSupported(fileType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "不支持的文件类型: " + fileType + "，支持: pdf, docx, md");
        }

        if (file.getSize() > 200 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_TOO_LARGE);
        }
    }

    private String saveFile(MultipartFile file, Long userId) {
        try {
            Path uploadDir = Paths.get(storagePath, "uploads", String.valueOf(userId));
            Files.createDirectories(uploadDir);

            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadDir.resolve(filename);

            file.transferTo(filePath.toFile());

            log.debug("文件保存成功: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String extractTitle(String filename) {
        if (filename == null) {
            return "未命名文档";
        }
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private ImportService currentProxy() {
        try {
            return (ImportService) AopContext.currentProxy();
        } catch (IllegalStateException ex) {
            return this;
        }
    }

    private int calculateEmbeddingProgress(int processed, int total) {
        if (total <= 0) {
            return 70;
        }
        return 50 + (processed * 20 / total);
    }

    private String arrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private void executeInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private Throwable unwrapFailure(Exception exception) {
        if (exception.getCause() != null) {
            return exception.getCause();
        }
        return exception;
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导入元数据序列化失败", e);
        }
    }

    public record ImportTaskOptions(Document.SensitivityLevel sensitivity,
                                    Long parentId,
                                    String description,
                                    String versionLabel,
                                    List<String> tags,
                                    int chunkSize,
                                    int chunkOverlap,
                                    boolean structuredChunk,
                                    String contentType,
                                    String originalFilename) {

        private static ImportTaskOptions from(CreateImportTaskRequest request) {
            List<String> normalizedTags = request.getTags() == null
                    ? List.of()
                    : new ArrayList<>(new LinkedHashSet<>(request.getTags().stream()
                    .map(tag -> tag == null ? "" : tag.trim())
                    .filter(tag -> !tag.isBlank())
                    .toList()));

            return new ImportTaskOptions(
                    request.getSensitivity(),
                    request.getParentId(),
                    request.getDescription(),
                    request.getVersionLabel().trim(),
                    normalizedTags,
                    request.getChunkSize(),
                    request.getChunkOverlap(),
                    Boolean.TRUE.equals(request.getStructuredChunk()),
                    request.getFile() != null ? request.getFile().getContentType() : "",
                    request.getFile() != null ? request.getFile().getOriginalFilename() : ""
            );
        }

        private Map<String, Object> toMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceType", "upload");
            metadata.put("versionLabel", versionLabel);
            metadata.put("tags", tags);
            metadata.put("chunkConfig", Map.of(
                    "sizeTokens", chunkSize,
                    "overlapTokens", chunkOverlap,
                    "structuredChunk", structuredChunk
            ));
            metadata.put("contentType", contentType == null ? "" : contentType);
            metadata.put("originalFilename", originalFilename == null ? "" : originalFilename);
            return metadata;
        }

        private String toDocumentMetadataJson() {
            return toJson(toMetadata());
        }

        private SmartChunkSplitter.ChunkOptions chunkOptions() {
            return new SmartChunkSplitter.ChunkOptions(chunkSize, chunkOverlap, structuredChunk);
        }
    }
}
