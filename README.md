# Base Org Frontend

基于 `Vite + Vanilla JS` 的多页面前端实现，覆盖：

- `A1` 登录页（本地登录 + SSO 入口）
- `A2` 注册页（实时校验 + 密码强度 + 自动登录）
- `B` 系统仪表盘（hash 子路由 + 指标/任务/告警）
- `C` 文档导入向导（四步流程 + 任务状态轮询 + 取消/重试）
- 文档模块（`#docs` 列表/目录 + `#doc-view` 左树右文档详情 + 详情页内联编辑）

项目总进度见：[PROJECT_PROGRESS.md](./PROJECT_PROGRESS.md)

## 目录结构

```text
.
├── login.html
├── register.html
├── dashboard.html
├── src
│   ├── css
│   │   ├── base.css
│   │   ├── components.css
│   │   └── pages
│   ├── js
│   │   ├── config
│   │   ├── pages
│   │   ├── services
│   │   └── utils
│   └── assets
│       └── icons
└── tests
    ├── unit
    └── e2e
```

## 快速开始

### 环境配置

复制 `.env.example` 为 `.env.local`：

```bash
VITE_API_MODE=mock
VITE_API_BASE_URL=
```

- `VITE_API_MODE=mock`：走本地 mock 服务
- `VITE_API_MODE=real`：走真实后端 API

### 启动服务

```bash
# 安装依赖
npm install --cache /tmp/base-org-npm-cache

# 启动前端
npm run dev
```

### 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin@system.com | Password123! | ADMIN |
| testuser1774517175 | Password123! | ADMIN |

### 后端启动

```bash
# 在仓库根目录执行，显式指定后端模块 pom，并提供本地开发必需配置
SPRING_PROFILES_ACTIVE=dev \
JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897' \
zsh -ic 'mvn39 -f backend/pom.xml spring-boot:run'
```

开发环境默认连接本地 PostgreSQL 数据库 `doc_assistant`

当前默认采用混合模式：

- 聊天模型走 OpenAI-compatible GPT
- 嵌入模型走本地 Ollama
- 导入文档时会直接生成并持久化向量

启动前请先准备 Ollama 模型：

```bash
ollama pull bge-m3
```

如果需要将嵌入切回 OpenAI，可覆盖：

```bash
export APP_AI_EMBEDDING_PROVIDER='OPENAI_COMPATIBLE'
export OPENAI_EMBEDDING_MODEL='text-embedding-3-small'
```

更多混合接入说明见：[docs/backend/ollama-hybrid-setup.md](./docs/backend/ollama-hybrid-setup.md)


## 构建与测试

```bash
npm run lint
npm run test
npm run build
```

Playwright e2e（当前环境若系统权限受限，请按下方方式）：

```bash
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers npx playwright install chromium
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers npm run test:e2e
```

## 环境变量

复制 `.env.example` 为 `.env.local`：

```bash
VITE_API_MODE=mock
VITE_API_BASE_URL=
```

- `VITE_API_MODE=mock`：走本地 mock 服务
- `VITE_API_MODE=real`：走真实后端 API

## QA流程

``` bash
前端消息
    ↓
[API Gateway] → 鉴权、限流
    ↓
[会话管理] → 加载历史、上下文
    ↓
[查询理解] → 清洗、改写、意图识别
    ↓
    ├─→ [工具调用] → API/计算器 → 结果整合
    ↓
[RAG 检索]
    ├─→ [Embedding] → 向量召回
    ├─→ [BM25] → 关键词召回
    ├─→ [知识图谱] → 关系召回
    ↓
[Rerank] → 精排 TopK
    ↓
[上下文组装] → Token 管理
    ↓
[Prompt 构建] → 指令 + 文档 + 历史 + 问题
    ↓
[LLM 生成] → 流式输出
    ↓
[后处理] → 引用标注、格式优化、缓存
    ↓
[返回前端] → SSE/WebSocket 流式响应
```

## 后端基线

- `backend/` 当前后端基线为 `Spring Boot 3.5.x + Spring AI Alibaba 版本体系 + OpenAI-compatible provider`
- 现有问答链路保留手写 `pgvector` 检索，不使用 Spring AI VectorStore DSL
- 当前后端已支持 GPT 聊天 + Ollama 嵌入的混合模式
