package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;

import java.util.Map;

/**
 * MCP 网关抽象。
 */
public interface McpGateway {

    Map<String, Object> call(ToolDescriptor descriptor, String question, Map<String, Object> arguments);
}
