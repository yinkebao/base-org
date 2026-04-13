package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaWebSearchProperties;
import com.baseorg.docassistant.dto.qa.tool.FetchedWebPage;
import com.baseorg.docassistant.dto.qa.tool.WebSearchCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * 基于 JDK HttpClient 的网页抓取实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpWebContentFetchGateway implements WebContentFetchGateway {

    private final AppQaWebSearchProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Optional<FetchedWebPage> fetch(WebSearchCandidate candidate) {
        if (candidate == null || candidate.getUrl() == null || candidate.getUrl().isBlank()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(candidate.getUrl()))
                    .header("User-Agent", "BaseOrgDocAssistant/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return Optional.of(FetchedWebPage.builder()
                    .url(candidate.getUrl())
                    .domain(candidate.getDomain())
                    .title(candidate.getTitle())
                    .publishedAt(candidate.getPublishedAt())
                    .content(response.body())
                    .suspicious(false)
                    .build());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("网页抓取失败: url={}, reason={}", candidate.getUrl(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("网页抓取异常: url={}, reason={}", candidate.getUrl(), e.getMessage());
            return Optional.empty();
        }
    }
}
