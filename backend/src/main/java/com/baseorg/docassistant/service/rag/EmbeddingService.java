package com.baseorg.docassistant.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本向量化服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AiRuntime aiRuntime;

    /**
     * 将文本转换为向量
     *
     * @param text 输入文本
     * @return 向量数组，如果向量化失败返回 null
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("输入文本为空，跳过向量化");
            return null;
        }

        if (!aiRuntime.embeddingAvailable()) {
            log.warn("EmbeddingModel 未配置，无法进行向量化。请检查当前 embedding provider 的模型配置是否已生效");
            return null;
        }

        try {
            log.debug("开始向量化文本，长度: {}", text.length());

            EmbeddingModel embeddingModel = aiRuntime.getEmbeddingModel();
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));

            if (response != null && !response.getResults().isEmpty()) {
                float[] embedding = response.getResults().get(0).getOutput();
                log.debug("向量化完成，维度: {}", embedding.length);
                return embedding;
            }

            log.warn("向量化响应为空");
            return null;
        } catch (Exception e) {
            log.error("向量化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 批量向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        if (!aiRuntime.embeddingAvailable()) {
            log.warn("EmbeddingModel 未配置，无法进行批量向量化。请检查当前 embedding provider 的模型配置是否已生效");
            return texts.stream().map(t -> (float[]) null).toList();
        }

        try {
            log.debug("开始批量向量化，数量: {}", texts.size());

            EmbeddingModel embeddingModel = aiRuntime.getEmbeddingModel();
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);

            if (response != null) {
                return response.getResults().stream()
                        .map(result -> result.getOutput())
                        .toList();
            }

            return texts.stream().map(t -> (float[]) null).toList();
        } catch (Exception e) {
            log.error("批量向量化失败: {}", e.getMessage(), e);
            return texts.stream().map(t -> (float[]) null).toList();
        }
    }

    /**
     * 检查向量化服务是否可用
     */
    public boolean isAvailable() {
        return aiRuntime.embeddingAvailable();
    }
}
