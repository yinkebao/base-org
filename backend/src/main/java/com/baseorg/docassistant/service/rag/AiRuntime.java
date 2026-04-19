package com.baseorg.docassistant.service.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 应用内部 AI 运行时抽象。
 */
public interface AiRuntime {

    boolean chatAvailable();

    boolean embeddingAvailable();

    ChatClient createChatClient();

    EmbeddingModel getEmbeddingModel();
}
