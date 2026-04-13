# GPT 聊天 + Ollama 嵌入接入说明

## 目标形态

- `chat` 走 OpenAI-compatible GPT
- `embedding` 走本地 Ollama
- 新导入文档会在入库时生成并保存向量

## 必需配置

启动前至少准备下面这些环境变量：

```bash
export OPENAI_API_KEY='your-openai-api-key'
export OPENAI_CHAT_MODEL='gpt-5.4'
export OLLAMA_BASE_URL='http://localhost:11434'
export OLLAMA_EMBEDDING_MODEL='bge-m3'
```

如需显式声明 provider，可补充：

```bash
export APP_AI_CHAT_PROVIDER='OPENAI_COMPATIBLE'
export APP_AI_EMBEDDING_PROVIDER='OLLAMA'
```

## 本地准备

```bash
ollama pull bge-m3
ollama serve
zsh -ic 'SPRING_PROFILES_ACTIVE=dev mvn39 -f backend/pom.xml spring-boot:run'
```

## 关键配置项

- `spring.ai.openai.api-key`：GPT 聊天鉴权
- `spring.ai.openai.base-url`：OpenAI-compatible 聊天服务地址
- `spring.ai.openai.chat.options.model`：聊天模型
- `spring.ai.ollama.base-url`：Ollama 服务地址
- `spring.ai.ollama.embedding.options.model`：嵌入模型
- `app.ai.chat.provider`：聊天 provider，默认 `OPENAI_COMPATIBLE`
- `app.ai.embedding.provider`：嵌入 provider，默认 `OLLAMA`

## 常见排查

- 启动日志里会输出 `chatProvider` 和 `embeddingProvider`，可先确认装配结果
- 如果聊天不可用，优先检查 `OPENAI_API_KEY` 和 `OPENAI_CHAT_MODEL`
- 如果嵌入不可用，优先检查 `OLLAMA_BASE_URL`、`OLLAMA_EMBEDDING_MODEL`，以及本地 Ollama 是否已启动
- 如果导入任务失败，优先看 `ImportService` 的异常日志，导入链路会在向量化失败时整体回滚
