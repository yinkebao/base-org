package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.FetchedWebPage;
import com.baseorg.docassistant.dto.qa.tool.WebSearchCandidate;

import java.util.Optional;

/**
 * 网页正文抓取网关。
 */
public interface WebContentFetchGateway {

    Optional<FetchedWebPage> fetch(WebSearchCandidate candidate);
}
