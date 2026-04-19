package com.baseorg.docassistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baseorg.docassistant.config.typehandler.JsonbTypeHandler;
import com.baseorg.docassistant.config.typehandler.VectorTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档块实体（用于向量检索）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "chunks", autoResultMap = true)
public class Chunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_id")
    private Long docId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("text")
    private String text;

    @TableField(value = "embedding", typeHandler = VectorTypeHandler.class)
    private String embedding;

    @TableField("tokens")
    private Integer tokens;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField("start_offset")
    private Integer startOffset;

    @TableField("end_offset")
    private Integer endOffset;

    @TableField("section_title")
    private String sectionTitle;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
