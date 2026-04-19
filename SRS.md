# SRS

## 1. 项目目标

构建面向企业内部的技术文档助手，提供文档导入、文档管理、问答检索与基础审计能力。

## 2. 当前范围

- 文档导入：上传文档并创建导入任务，记录状态与进度
- 文档管理：文档树、文档列表、文档详情、面包屑、内联编辑、图片上传
- 检索与 QA：基于向量检索返回回答与来源
- 管理监控：指标、告警、健康检查
- 认证：登录、注册、SSO 入口、用户名检查

## 3. 核心数据模型

**Document**

- `doc_id: UUID`
- `title: string`
- `source_url: string`
- `owner_id: UUID`
- `dept: string`
- `version: string`
- `sensitivity: enum(public|internal|confidential|secret)`
- `content_markdown: text`

**Chunk**

- `chunk_id: UUID`
- `doc_id: UUID`
- `chunk_index: int`
- `text: text`
- `embedding: vector`
- `tokens: int`
- `metadata: jsonb`

**ImportTask**

- `task_id: UUID`
- `owner_id: UUID`
- `filename: string`
- `status: enum`
- `progress: int`
- `result_doc_id: UUID`

**AuditLog**

- `audit_id, user_id, action, resource_type, resource_id, timestamp, details(jsonb)`

**Template**

- `template_id, name, category, content, variables, version`

## 4. API 规范摘要

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `GET /api/v1/auth/check-username`
- `GET /api/v1/auth/sso/start`
- `POST /api/v1/documents/import`
- `GET /api/v1/documents/import/{taskId}`
- `GET /api/v1/documents/import/recent`
- `GET /api/v1/documents/tree`
- `GET /api/v1/documents`
- `GET /api/v1/documents/{id}`
- `PUT /api/v1/documents/{id}`
- `GET /api/v1/documents/{id}/breadcrumb`
- `POST /api/v1/documents/{id}/assets/images`
- `GET /api/v1/documents/{id}/assets/images/{filename}`
- `POST /api/v1/qa`
- `GET /api/v1/admin/metrics`
- `GET /api/v1/admin/alerts`
- `GET /api/v1/admin/health`

## 5. Web 页面

### 页面 A1 — 登录 / 单点登录

- 用户名/邮箱登录
- 密码校验与错误提示
- SSO 登录入口

### 页面 A2 — 注册

- 用户名、邮箱、密码校验
- 注册成功后自动登录或跳转登录

### 页面 B — 系统仪表盘

- 系统指标
- 最近导入任务
- 告警列表

### 页面 C — 文档导入向导

- 选择来源
- 填写 metadata
- 配置分块
- 提交导入并轮询状态

### 页面 D — 文档管理与详情

- 文档树与列表
- 文档详情渲染
- 原地内联编辑
- 图片上传与面包屑

### 页面 E — 检索与 QA

- 查询输入
- AI 回答卡片
- 来源列表
- 敏感来源过滤提示

## 6. 安全与合规

- 导入时强制选择 `sensitivity`
- 检索结果在后端执行 ACL 过滤
- 发往外部模型的上下文需最小化
- 每次 LLM 请求记录 `promptHash`

## 7. 验收标准

- 可成功导入 PDF/Word/Markdown 文档
- 检索结果召回率 ≥ 80%
- AI 回答正确率 ≥ 85%
- 文档详情页支持内联编辑并可保存
- 未授权用户无法检索敏感文档
