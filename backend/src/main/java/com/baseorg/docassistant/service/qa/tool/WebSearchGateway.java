package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.WebSearchCandidate;

import java.util.List;

/**
 * 外部搜索候选获取网关。
 */
public interface WebSearchGateway {

    List<WebSearchCandidate> search(ToolDescriptor descriptor, List<String> queries, int maxResults);
}
