package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolStep;

/**
 * 工具执行过程观察者。
 */
public interface ToolExecutionObserver {

    default void onToolStart(ToolDescriptor descriptor, ToolStep step) {
    }

    default void onToolResult(ToolDescriptor descriptor, ToolStep step, ToolExecutionResult result) {
    }

    default void onToolError(ToolDescriptor descriptor, ToolStep step, String message) {
    }

    default void onDiagram(ToolDescriptor descriptor, ToolStep step, DiagramPayload diagram) {
    }
}
