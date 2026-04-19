package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaMcpProperties;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认 MCP 网关实现。
 * <p>
 * 优先走真实远程 MCP；若对应 server 未配置或不可用，则统一返回降级结果，
 * 避免影响 QA 主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMcpGateway implements McpGateway {

    private static final String CLIENT_NAME = "base-org-doc-assistant";
    private static final String CLIENT_TITLE = "BaseOrg Doc Assistant";
    private static final String CLIENT_VERSION = "0.0.1-SNAPSHOT";

    private final AppQaMcpProperties mcpProperties;
    private final ConcurrentMap<String, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> call(ToolDescriptor descriptor, String question, Map<String, Object> arguments) {
        AppQaMcpProperties.ServerConfig server = resolveServerConfig(descriptor);
        Map<String, Object> normalizedArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        if (server == null) {
            log.warn("MCP 调用跳过: server={}, toolId={}, reason=未找到配置",
                    descriptor == null ? null : descriptor.getServerName(),
                    descriptor == null ? null : descriptor.getToolId());
            return unavailablePayload(descriptor, question, normalizedArguments, "未找到对应的 MCP server 配置");
        }
        if (!server.isEnabled()) {
            log.warn("MCP 调用跳过: server={}, toolId={}, reason=server 已禁用",
                    descriptor.getServerName(), descriptor.getToolId());
            return unavailablePayload(descriptor, question, normalizedArguments, "MCP server 已禁用");
        }
        if (isBlank(server.getBaseUrl())) {
            log.warn("MCP 调用跳过: server={}, toolId={}, reason=缺少 baseUrl",
                    descriptor.getServerName(), descriptor.getToolId());
            return unavailablePayload(descriptor, question, normalizedArguments, "MCP server 缺少 baseUrl 配置");
        }
        if (requiresApiKey(server) && isBlank(server.getApiKey())) {
            log.warn("MCP 调用跳过: server={}, toolId={}, reason=缺少 API Key",
                    descriptor.getServerName(), descriptor.getToolId());
            return unavailablePayload(descriptor, question, normalizedArguments, "MCP server 缺少 API Key，已自动降级");
        }

        try {
            log.info("开始调用 MCP: server={}, tool={}, baseUrl={}, argumentKeys={}, hasApiKey={}",
                    descriptor.getServerName(),
                    resolveRemoteToolName(descriptor),
                    server.getBaseUrl(),
                    normalizedArguments.keySet(),
                    !isBlank(server.getApiKey()));
            McpSyncClient client = clientCache.computeIfAbsent(
                    descriptor.getServerName(),
                    ignored -> createClient(server)
            );
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    resolveRemoteToolName(descriptor),
                    normalizedArguments
            ));
            log.info("MCP 调用完成: server={}, tool={}, isError={}, contentCount={}",
                    descriptor.getServerName(),
                    resolveRemoteToolName(descriptor),
                    Boolean.TRUE.equals(result.isError()),
                    result.content() == null ? 0 : result.content().size());
            Map<String, Object> payload = normalizePayload(descriptor, question, normalizedArguments, result);
            if (Boolean.TRUE.equals(payload.get("isError"))) {
                log.warn("MCP 工具返回错误: server={}, tool={}, summary={}, errorMessage={}, content={}, meta={}",
                        descriptor.getServerName(),
                        resolveRemoteToolName(descriptor),
                        clipForLog(payload.get("summary")),
                        clipForLog(payload.get("errorMessage")),
                        clipForLog(payload.get("content")),
                        clipForLog(payload.get("meta")));
            }
            return payload;
        } catch (Exception e) {
            if (isIgnorableLifecycleNoise(e)) {
                log.debug("MCP 生命周期后台告警已降噪: server={}, tool={}, reason={}",
                        descriptor.getServerName(),
                        resolveRemoteToolName(descriptor),
                        e.getMessage());
            }
            clientCache.remove(descriptor.getServerName());
            log.warn("MCP 调用失败: server={}, tool={}, reason={}",
                    descriptor.getServerName(), resolveRemoteToolName(descriptor), e.getMessage());
            return unavailablePayload(descriptor, question, normalizedArguments, "MCP 调用失败: " + e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        clientCache.values().forEach(client -> {
            try {
                client.closeGracefully();
            } catch (Exception e) {
                log.debug("关闭 MCP 客户端失败: {}", e.getMessage());
            }
        });
        clientCache.clear();
    }

    private AppQaMcpProperties.ServerConfig resolveServerConfig(ToolDescriptor descriptor) {
        if (descriptor == null || isBlank(descriptor.getServerName())) {
            return null;
        }
        return mcpProperties.getServers().get(descriptor.getServerName());
    }

    private boolean requiresApiKey(AppQaMcpProperties.ServerConfig server) {
        return !"none".equalsIgnoreCase(server.getAuthMode());
    }

    private McpSyncClient createClient(AppQaMcpProperties.ServerConfig server) {
        URI transportUri = URI.create(buildTransportUrl(server));
        String origin = transportUri.getScheme() + "://" + transportUri.getAuthority();
        String endpoint = buildEndpoint(transportUri);
        Duration timeout = Duration.ofSeconds(Math.max(server.getRequestTimeoutSeconds(), 1));
        log.info("初始化 MCP 客户端: baseUrl={}, authMode={}, endpoint={}, timeoutSeconds={}",
                sanitizeUrlForLog(server.getBaseUrl()), server.getAuthMode(), sanitizeEndpointForLog(endpoint), timeout.getSeconds());

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(origin)
                .endpoint(endpoint)
                .connectTimeout(timeout)
                .customizeRequest(requestBuilder -> {
                    requestBuilder.setHeader("Accept", "application/json, text/event-stream");
                    requestBuilder.setHeader("User-Agent", CLIENT_NAME + "/" + CLIENT_VERSION);
                    server.getHeaders().forEach(requestBuilder::setHeader);
                    if (shouldUseBearerAuth(server)) {
                        requestBuilder.setHeader("Authorization", "Bearer " + server.getApiKey());
                    }
                })
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_TITLE, CLIENT_VERSION))
                .build();
        client.initialize();
        logDiscoveredTools(client, server);
        log.info("MCP 客户端初始化完成: baseUrl={}, authMode={}", sanitizeUrlForLog(server.getBaseUrl()), server.getAuthMode());
        return client;
    }

    private Map<String, Object> normalizePayload(ToolDescriptor descriptor,
                                                 String question,
                                                 Map<String, Object> arguments,
                                                 McpSchema.CallToolResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", true);
        payload.put("serverName", descriptor.getServerName());
        payload.put("remoteToolName", resolveRemoteToolName(descriptor));
        payload.put("question", question);
        payload.put("arguments", arguments);
        boolean isError = Boolean.TRUE.equals(result.isError());
        payload.put("isError", isError);

        Object structuredContent = result.structuredContent();
        if (structuredContent != null) {
            payload.put("structuredContent", structuredContent);
            if (structuredContent instanceof Map<?, ?> map) {
                map.forEach((key, value) -> payload.put(String.valueOf(key), value));
            } else {
                payload.put("data", structuredContent);
            }
        }

        List<String> textContents = result.content().stream()
                .map(content -> content instanceof McpSchema.TextContent textContent ? textContent.text() : String.valueOf(content))
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .toList();
        if (!textContents.isEmpty()) {
            payload.put("content", textContents);
        }
        if (result.meta() != null && !result.meta().isEmpty()) {
            payload.put("meta", result.meta());
        }
        String summary = extractSummary(payload, textContents, descriptor);
        payload.put("summary", summary);
        if (isError) {
            payload.put("errorMessage", summary);
        }
        return payload;
    }

    private String extractSummary(Map<String, Object> payload, List<String> textContents, ToolDescriptor descriptor) {
        Object existingSummary = payload.get("summary");
        if (existingSummary != null && !String.valueOf(existingSummary).isBlank()) {
            return String.valueOf(existingSummary);
        }
        if (!textContents.isEmpty()) {
            String merged = String.join("\n", textContents).trim();
            return merged.length() <= 240 ? merged : merged.substring(0, 240) + "...";
        }
        if (payload.get("results") instanceof List<?> results) {
            return descriptor.getDisplayName() + " 返回 " + results.size() + " 条结构化结果";
        }
        return descriptor.getDisplayName() + " 已完成调用";
    }

    private Map<String, Object> unavailablePayload(ToolDescriptor descriptor,
                                                   String question,
                                                   Map<String, Object> arguments,
                                                   String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", false);
        payload.put("serverName", descriptor == null ? null : descriptor.getServerName());
        payload.put("remoteToolName", descriptor == null ? null : resolveRemoteToolName(descriptor));
        payload.put("question", question);
        payload.put("arguments", arguments == null ? Map.of() : arguments);
        payload.put("summary", reason);
        payload.put("errorMessage", reason);
        return payload;
    }

    private String resolveRemoteToolName(ToolDescriptor descriptor) {
        String remoteToolName = isBlank(descriptor.getRemoteToolName()) ? descriptor.getToolId() : descriptor.getRemoteToolName();
        if ("tavily".equalsIgnoreCase(descriptor.getServerName())) {
            return remoteToolName.replace('-', '_');
        }
        return remoteToolName;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean shouldUseBearerAuth(AppQaMcpProperties.ServerConfig server) {
        return "bearer".equalsIgnoreCase(server.getAuthMode()) && !isBlank(server.getApiKey());
    }

    private String buildTransportUrl(AppQaMcpProperties.ServerConfig server) {
        if (!"query".equalsIgnoreCase(server.getAuthMode())
                || isBlank(server.getApiKey())
                || isBlank(server.getApiKeyQueryParam())) {
            return server.getBaseUrl();
        }
        String separator = server.getBaseUrl().contains("?") ? "&" : "?";
        return server.getBaseUrl()
                + separator
                + URLEncoder.encode(server.getApiKeyQueryParam(), StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(server.getApiKey(), StandardCharsets.UTF_8);
    }

    private String buildEndpoint(URI transportUri) {
        String path = isBlank(transportUri.getRawPath()) ? "/mcp" : transportUri.getRawPath();
        if (isBlank(transportUri.getRawQuery())) {
            return path;
        }
        return path + "?" + transportUri.getRawQuery();
    }

    private void logDiscoveredTools(McpSyncClient client, AppQaMcpProperties.ServerConfig server) {
        try {
            McpSchema.ListToolsResult listToolsResult = client.listTools();
            List<String> toolNames = listToolsResult.tools() == null
                    ? List.of()
                    : listToolsResult.tools().stream().map(McpSchema.Tool::name).filter(Objects::nonNull).toList();
            log.info("MCP 服务工具发现: baseUrl={}, authMode={}, tools={}",
                    sanitizeUrlForLog(server.getBaseUrl()),
                    server.getAuthMode(),
                    toolNames);
            if (server.getExpectedToolNames() != null && !server.getExpectedToolNames().isEmpty()) {
                List<String> missingTools = server.getExpectedToolNames().stream()
                        .filter(expected -> !toolNames.contains(expected))
                        .toList();
                if (!missingTools.isEmpty()) {
                    log.warn("MCP 服务缺少预期工具: baseUrl={}, expected={}, actual={}",
                            sanitizeUrlForLog(server.getBaseUrl()),
                            missingTools,
                            toolNames);
                }
            }
        } catch (Exception e) {
            log.warn("MCP 工具发现失败: baseUrl={}, authMode={}, reason={}",
                    sanitizeUrlForLog(server.getBaseUrl()),
                    server.getAuthMode(),
                    e.getMessage());
        }
    }

    private String sanitizeUrlForLog(String url) {
        if (isBlank(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, uri.getFragment()).toString();
        } catch (Exception e) {
            return url.replaceAll("([?&][^=]+)=([^&]+)", "$1=***");
        }
    }

    private String sanitizeEndpointForLog(String endpoint) {
        if (isBlank(endpoint)) {
            return "";
        }
        return endpoint.replaceAll("([?&][^=]+)=([^&]+)", "$1=***");
    }

    /**
     * Tavily 在后台生命周期监听里会尝试连接 SSE 流，但远端不一定支持对应路径，
     * 这类 405 告警不会影响当前同步 callTool 的成功结果，因此只作为降噪日志处理。
     */
    private boolean isIgnorableLifecycleNoise(Exception exception) {
        String message = exception == null || exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return message.contains("sse stream") && message.contains("405");
    }

    private String clipForLog(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace('\n', ' ').trim();
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500) + "...";
    }
}
