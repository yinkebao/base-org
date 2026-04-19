package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.config.AppQaToolProperties;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * QA 工具注册中心。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QAToolRegistry {

    private final List<QAToolHandler> handlers;
    private final AppQaToolProperties toolProperties;

    private final Map<ToolType, QAToolHandler> handlerByType = new EnumMap<>(ToolType.class);
    private final Map<String, ToolDescriptor> descriptorById = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        handlerByType.clear();
        handlers.forEach(handler -> handlerByType.put(handler.getSupportedType(), handler));

        descriptorById.clear();
        toolProperties.getCatalog().stream()
                .map(this::toDescriptor)
                .filter(descriptor -> descriptor.getType() != null)
                .filter(descriptor -> handlerByType.containsKey(descriptor.getType()))
                .sorted(Comparator.comparingInt(ToolDescriptor::getPriority))
                .forEach(descriptor -> descriptorById.put(descriptor.getToolId(), descriptor));

        log.info("QA 工具注册完成: handlerCount={}, descriptorCount={}", handlerByType.size(), descriptorById.size());
    }

    public List<ToolDescriptor> listEnabledDescriptors() {
        return descriptorById.values().stream()
                .filter(ToolDescriptor::isEnabled)
                .sorted(Comparator.comparingInt(ToolDescriptor::getPriority))
                .toList();
    }

    public Optional<ToolDescriptor> findDescriptor(String toolId) {
        return Optional.ofNullable(descriptorById.get(toolId));
    }

    public Optional<QAToolHandler> findHandler(ToolType toolType) {
        return Optional.ofNullable(handlerByType.get(toolType));
    }

    private ToolDescriptor toDescriptor(AppQaToolProperties.CatalogItem item) {
        return ToolDescriptor.builder()
                .toolId(item.getToolId())
                .type(item.getType())
                .providerType(item.getProviderType())
                .displayName(item.getDisplayName())
                .description(item.getDescription())
                .serverName(item.getServerName())
                .remoteToolName(item.getRemoteToolName())
                .enabled(item.isEnabled())
                .readOnly(item.isReadOnly())
                .supportsStreaming(item.isSupportsStreaming())
                .priority(item.getPriority())
                .keywords(item.getKeywords())
                .metadata(item.getMetadata())
                .build();
    }
}
