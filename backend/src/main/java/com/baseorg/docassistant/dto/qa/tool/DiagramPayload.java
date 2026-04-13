package com.baseorg.docassistant.dto.qa.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mermaid 图表载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramPayload {
    private String diagramId;
    private String diagramType;
    private String title;
    private String mermaidDsl;
    private List<String> sourceStepIds;
}
