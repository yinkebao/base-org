# 技术文档助手

基于企业 RAG 能力与 Spring 生态构建的全栈文档检索、导入与编辑系统。

**技术栈**：`Vite + Vanilla JS`（前端）+ `Spring Boot 3.5.x + Spring AI Alibaba`（后端）+ `PostgreSQL + pgvector`（向量存储）

项目总进度见：[PROJECT_PROGRESS.md](./PROJECT_PROGRESS.md)

---

## 核心能力

- **企业知识检索（RAG）**：混合向量召回 + BM25 关键词检索 + Rerank 精排
- **文档导入与向量化**：支持 PDF / Word / Markdown，自动分块并持久化向量
- **文档管理与内联编辑**：文档树、详情页、原地编辑、图片上传
- **QA 工具链**：联网搜索（Tavily）、飞书 MCP、蓝湖 MCP、Mermaid 图表生成
- **模板管理与审计**：操作审计、审计日志保存 365 天

---

## 目录结构

```text
.
├── frontend/              # 前端项目
│   ├── index.html         # 入口（重定向到登录页）
│   ├── login.html         # 登录页
│   ├── register.html      # 注册页
│   ├── dashboard.html     # 系统仪表盘
│   ├── src
│   │   ├── css/
│   │   ├── js/
│   │   │   ├── config/
│   │   │   ├── pages/
│   │   │   ├── services/
│   │   │   └── utils/
│   │   └── assets/icons/
│   ├── tests/
│   │   ├── unit/
│   │   └── e2e/
│   ├── package.json
│   └── vite.config.js
├── backend/               # Spring Boot 后端
│   └── src/main/
│       ├── java/com/baseorg/docassistant/
│       └── resources/
│           ├── application.yml
│           └── application-dev.yml
└── docs/                  # 架构与集成文档
    ├── API_CONTRACT.md
    ├── PRD.md
    ├── backend/
    ├── integration/
    └── project-management/
```

---

## 快速开始

### 前置依赖

| 依赖 | 说明 |
|------|------|
| Node.js ≥ 18 | 前端构建 |
| Java 21 + Maven 3.9 | 后端编译运行（本地 alias `mvn39`） |
| PostgreSQL 15+ | 主数据库（需开启 pgvector 扩展） |
| Ollama | 本地嵌入模型（默认 `bge-m3`） |

### 环境配置

在 `frontend/` 目录下复制 `.env.example` 为 `.env`：

```bash
cd frontend
cp .env.example .env
```

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_MODE` | `mock`：本地 mock；`real`：真实后端 | `mock` |
| `VITE_API_BASE_URL` | 后端地址 | 空（`real` 模式需填写） |
| `OPENAI_API_KEY` | OpenAI-compatible 聊天模型 Key | — |
| `OPENAI_BASE_URL` | OpenAI-compatible 服务地址 | — |
| `TAVILY_API_KEY` | 联网搜索 API Key | — |

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 启动后端

1. 准备 Ollama 嵌入模型（默认配置）：

```bash
ollama pull bge-m3
```

2. 启动 Spring Boot（使用 `mvn39` alias）：

```bash
SPRING_PROFILES_ACTIVE=dev \
JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897' \
zsh -ic 'mvn39 -f backend/pom.xml spring-boot:run'
```

后端默认监听 `localhost:8080`，连接本地 PostgreSQL 数据库 `doc_assistant`（用户名/密码：`postgres/postgres`）。

---

## AI 模型配置

当前默认采用**混合模式**：

| 用途 | 提供商 | 模型 |
|------|--------|------|
| 聊天生成 | OpenAI-compatible | `gpt-5.4` |
| 向量嵌入 | Ollama（本地） | `bge-m3` |

如需将嵌入切换为 OpenAI：

```bash
export APP_AI_EMBEDDING_PROVIDER='OPENAI_COMPATIBLE'
export OPENAI_EMBEDDING_MODEL='text-embedding-3-small'
```

更多混合接入说明见：[docs/backend/ollama-hybrid-setup.md](./docs/backend/ollama-hybrid-setup.md)

---

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin@system.com | Password123! | ADMIN |
| testuser1774517175 | Password123! | ADMIN |

---

## 构建与测试

```bash
cd frontend

# 代码检查
npm run lint

# 单元测试（Vitest）
npm run test

# 生产构建
npm run build
```

Playwright E2E 测试（系统权限受限时）：

```bash
cd frontend
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers npx playwright install chromium
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers npm run test:e2e
```

---

## QA 流程

```
前端消息
    ↓
[API Gateway] → 鉴权、限流
    ↓
[查询理解] → 清洗、改写、意图识别
    ↓
    ├─→ [工具调用] → 联网搜索 / 飞书 MCP / 蓝湖 MCP / Mermaid 图表
    ↓
[RAG 检索]
    ├─→ [Embedding] → 向量召回（pgvector）
    ├─→ [BM25]      → 关键词召回
    └─→ [知识图谱]  → 关系召回
    ↓
[Rerank] → 精排 TopK
    ↓
[Prompt 构建] → 指令 + 文档 + 历史 + 问题
    ↓
[LLM 生成] → 流式输出
    ↓
[返回前端] → SSE/WebSocket 流式响应
```

---

## 安全与合规

- **数据分级**：`public | internal | confidential | secret`
- **ACL 强制过滤**：检索时在向量查询后执行 metadata-level filter（后端强制）
- **敏感信息处理**：发送给外部 LLM 的上下文需剔除敏感字段
- **审计**：每次 LLM 请求记录 `promptHash`，审计日志保存 365 天

---

## 性能指标

- API 可用性：99.9%
- 检索 + 生成 P95：≤ 3s（不含外部 LLM 延迟）
- 向量 DB：支持 10M+ chunk 检索
- 50 并发下 P95 latency ≤ 3s

---

## 参考文档

| 文档 | 说明 |
|------|------|
| [SRS.md](./SRS.md) | 完整需求规格说明书 |
| [PROJECT_PROGRESS.md](./PROJECT_PROGRESS.md) | 项目进度跟踪 |
| [docs/API_CONTRACT.md](./docs/API_CONTRACT.md) | API 契约 |
| [docs/PRD.md](./docs/PRD.md) | 产品需求文档 |
| [docs/integration/](./docs/integration/) | 前后端联调测试 |
