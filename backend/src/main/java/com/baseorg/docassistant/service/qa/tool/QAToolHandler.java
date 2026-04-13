package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.ToolExecutionRequest;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolType;

/**
 * QA 工具执行 SPI。
 */
public interface QAToolHandler {

    ToolType getSupportedType();

    ToolExecutionResult execute(ToolExecutionRequest request);
}
