package com.baseorg.docassistant.dto.importtask;

import com.baseorg.docassistant.entity.Document;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建导入任务请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateImportTaskRequest {

    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    private Document.SensitivityLevel sensitivity;

    private Long parentId;

    private String description;

    @NotBlank(message = "版本号不能为空")
    @Size(max = 64, message = "版本号长度不能超过64")
    private String versionLabel;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @NotNull(message = "Chunk 大小不能为空")
    @Min(value = 100, message = "Chunk 大小需在 100-2000 之间")
    @Max(value = 2000, message = "Chunk 大小需在 100-2000 之间")
    private Integer chunkSize;

    @NotNull(message = "Overlap 不能为空")
    @Min(value = 0, message = "重叠 token 不能为负数")
    private Integer chunkOverlap;

    @NotNull(message = "结构化分块配置不能为空")
    private Boolean structuredChunk;
}
