# 技术文档 & 需求文档助手 — 需求规格说明书（SRS）

> 版本：1.0
> 读者：产品经理、架构师、后端工程师、前端工程师、SRE、安全与合规模块负责人
> 概要：基于企业 RAG 能力与 Spring 生态，构建“技术文档 & 需求文档助手”（下称本系统），提供检索问答、需求草案生成、文档补全、来源可追溯与审批发布工作流。

---

# 1 目标与范围（概述）

**目标**：为产品/研发/测试/运维等团队提供一套可生产化使用的文档辅助系统，核心能力包括：企业知识检索（RAG）、文档生成/补全、需求模板化生成、变更审批与审计。系统需满足企业合规（数据分级/访问控制）、可观测与成本控制要求。

**范围（MVP）**

* 文档导入（支持上传、URL、S3/对象存储触发）
* 向量索引（chunk/embedding → 向量 DB）
* 基于检索的 QA（返回答案 + 来源片段）
* 需求草稿生成（模板驱动）
* 基本权限与审计日志

**非范围（1.0 后考虑）**

* 自动 Agent 直接修改主文档（必须走审批）
* 多模型本地 failover（可做扩展）
* 全量自然语言编程能力（较高风险）

---

# 2 关键角色与用户故事

**主要角色**

* 产品经理（PM） — 使用需求模板生成、审阅与批准草稿。
* 开发工程师（Dev） — 在 IDE 中查找接口与实现细节、请求补全。
* 测试工程师（QA） — 根据需求生成测试用例草稿、提交缺陷。
* 文档管理员（Doc Admin） — 管理文档来源、更新策略、权限。
* 系统管理员（SRE / Sec） — 监控、部署、合规配置。

**典型用户故事**

1. 作为 PM，我要上传 Confluence 导出文档并让系统建立检索索引，以便快速查询历史设计决策。
2. 作为 Dev，我要查询“接口 X 的鉴权逻辑”，并得到准确的段落与源码引用。
3. 作为 PM，我要基于“需求模板 A”和若干输入快速生成需求草稿并提交审批。
4. 作为 Doc Admin，我要为高敏感度文档设定检索 ACL，确保只有授权用户能检索到相应片段。

---

# 3 全局非功能性需求（NFRs）

* 可用性：API 可用性目标 99.9%。
* 响应时间：检索 + 生成的 P95 ≤ 3s（不含外部 LLM 网络延迟），对外LLM延迟需在 SLA 内说明。
* 扩展性：向量 DB 支持水平扩展，单节点可支撑 10M+ chunk 的检索。
* 安全：所有外部模型调用应走受控出口，敏感字段脱敏，审计日志完整存储 365 天（可配置）。
* 合规：支持数据分类（public/internal/confidential/secret）与基于 metadata 的 ACL 过滤。
* 成本控制：实现 token 用量统计与预算告警、低/高优先级请求区分。
* 可观测：Prometheus 指标、OpenTelemetry traces、Grafana 仪表盘。

---

# 4 技术栈建议（关键项）

（下列技术项在架构文档中作为参考选型，后续可替换）

* Java 应用框架： Spring Boot — 作为 API 网关与鉴权/审计层。
* Spring AI（调用 LLM / prompt 管理）： Spring AI — 用于 Java 原生接入 LLM 与 prompt template。
* LLM 供应商（示例）： OpenAI / Anthropic — 生产阶段供选择。
* 向量存储（自托管）： PostgreSQL + pgvector；或高性能替代： Milvus；SaaS 替代： Pinecone。
* RAG / pipeline（可选 Python 引擎）： LangChain 或 LlamaIndex（若采用混合架构）。
* 监控/告警： Prometheus + Grafana。
* Secrets 管理： HashiCorp Vault / 云 KMS。

> 注：以上实体在文档中首次提及并已标注；后文引用时按技术名词呈现，不再重复实体包装。

---

# 5 功能需求（详细、可验收）

## 功能组 A — 文档导入与索引（Ingestion）

1. 支持来源：上传文件（PDF/Word/Markdown）、URL 拉取、S3 存储路径触发、Confluence 导出包。
2. 解析：自动识别格式、提取正文、表格、代码块，并产出 chunk；支持 OCR（可选模块）。
3. Chunk 策略：默认 500 tokens ±200，overlap 100 tokens（可配置）；对代码或表格使用结构化 chunk（保留原始标识）。
4. Metadata：为每 chunk 保存 `doc_id, source, author, version, dept, sensitivity, tags`；必须在索引记录中保存并用于检索过滤。
5. 存储：向量与 metadata 写入向量 DB；原始文件存 S3（或对象存储），db 保存引用 URL。
6. API：`POST /api/v1/documents/import`（异步任务，返回 taskId；任务有状态查询接口）。

**验收准则**：上传 10 个混合格式文档，系统成功完成 parsing、chunk、embedding 并且在向量 DB 能检索出相应 chunk（recall@5 ≥ 0.8，基于人工标注测试集）。

---

## 功能组 B — 检索问答（QA）

1. 接口：`POST /api/v1/qa`，参数 `{query, userId, filters:{dept,sensitivity}, topK, rerank}`。
2. 流程：query → embed → vector search topK → metadata filter（ACL）→ 可选 rerank → prompt 组装 → LLM 调用 → 返回 `{answer, sources:[{docId, chunkId, score, excerpt}], cost}`。
3. 输出要求：答案必须带 `sources` 列表（最少包含 1 条来源），并指明相关段落偏移。
4. 失败处理：若外部 LLM 超时或失败，返回降级模式（只返回检索片段与摘要），并在响应中标注 `degraded:true`。
5. 安全：在构造发送到 LLM 的上下文时，系统应剔除敏感字段（按 sensitivity 策略）。

**验收准则**：针对 50 条业务 QA，系统返回答案并且人工评估正确率 ≥ 85%；在 LLM 超时场景下能正确返回降级结果且 HTTP 状态码 200 + `degraded:true`。

---

## 功能组 C — 需求草稿生成与模板管理

1. Template 管理：支持模板 CRUD（模板变量定义、示例、约束），模板可版本化并进行权限控制。
2. 生成接口：`POST /api/v1/generate/requirement` 参数 `{templateId, inputs, userId, approvers}`，返回 draftId + generated_text + suggested_changes。
3. 编辑与协作：草稿支持在线编辑（富文本），每次编辑记录版本并保存变更 diff。
4. 审批流：草稿提交后进入审批队列，审批通过后才允许写回 Confluence/Git/存储库（写回需二次确认）。
5. Traceability：对生成内容记录所用的 `sources` 与 prompt（用于审计与复现）。

**验收准则**：使用模板生成 20 份需求草稿，编辑并提交审批流程，审批记录完整且写回动作在审批通过后执行成功。

---

## 功能组 D — 审计、日志、权限（基础合规）

1. 审计项：每次 ingestion、检索（QA）、生成、编辑、审批、写回操作都记录详细审计（userId, timestamp, action, resources, promptHash, sources, tokenCost）。
2. 权限：基于 RBAC 的访问控制（角色 → 权限）；数据级 ACL（chunk-level）在检索时进行强制过滤。
3. 日志保存策略：审计日志与行为日志至少保存 365 天（可配置），敏感内容加密存储。
4. 告警：当 token 花费或异常请求增幅超过阈值触发预算告警与安全告警。

**验收准则**：模拟未授权用户检索高敏感文档，检索结果应为空；审计记录可查询且包含必要字段。

---

# 6 数据模型（核心表 / 文档结构）

**Document**

* `doc_id: UUID`
* `title: string`
* `source_url: string`
* `author: string`
* `dept: string`
* `version: string`
* `sensitivity: enum` (`public|internal|confidential|secret`)
* `created_at, updated_at`
* `object_store_url`（原始文件）

**Chunk**

* `chunk_id: UUID`
* `doc_id: UUID`（FK）
* `chunk_index: int`
* `text: text`（仅保留摘要或引用，全文保存在对象存储）
* `embedding: vector`（写入向量 DB）
* `tokens: int`
* `metadata: jsonb`（包含 code_flag, table_flag, language 等）
* `sensitivity`（继承或上覆盖）

**AuditLog**

* `audit_id, user_id, action, resource_type, resource_id, timestamp, details(jsonb)`

**Draft**

* `draft_id, template_id, owner_id, content, status(pending/approved/rejected), versions[]`

---

# 7 API 规范（摘要，OpenAPI 级别应另附完整文档）

* `POST /api/v1/documents/import` — 上传或触发导入（返回 taskId）
* `GET /api/v1/documents/import/{taskId}` — 查询导入状态
* `POST /api/v1/qa` — 提交问答请求，返回 answer + sources + cost
* `POST /api/v1/generate/requirement` — 生成需求草稿
* `GET /api/v1/drafts/{id}` — 获取草稿与版本历史
* `POST /api/v1/drafts/{id}/submit` — 提交审批
* `POST /api/v1/admin/templates` — 模板管理（管理员）
* `GET /api/v1/audit/logs` — 审计查询（管理员/合规）

每个接口必须支持标准分页、过滤、trace-id（用于链路追踪）。

---

# 8 Web 页面与 UI 交互逻辑（逐页详述）

> 下面详述主要 Web 页面（角色视角），包含 UI 元素、状态、事件、前端到后端交互（API）、校验与错误处理，便于产品与前端实现。

## 页面 A1 — 登录 / 单点登录

- **角色**：已有账户的现有用户
- **核心元素**：
  - 用户名/邮箱输入框
  - 密码输入框
  - “忘记密码？”链接（点击跳转到密码重置流程）
  - 登录按钮（主按钮样式）
  - “使用企业SSO登录”按钮（OIDC/SAML，次级按钮样式）
  - 页面底部的“还没有账户？**注册**”链接（引导至页面 A2）
  - 页面顶部的语言切换器（下拉菜单或图标）
- **交互逻辑**：
  - **SSO优先**：点击SSO按钮重定向至企业身份提供商；认证成功后自动跳转到 Dashboard；失败时返回清晰错误提示（401/403）
  - **本地登录逻辑**：
    - 提交用户名/密码进行本地身份验证
    - 验证成功：跳转到 Dashboard
    - 验证失败：显示“用户名或密码错误”提示
    - 账户未激活：提示“请先激活您的账户，检查注册邮箱”
    - 账户被锁定：显示“账户已锁定，请联系管理员”并隐藏登录按钮（或置灰）
- **安全策略**：
  - 登录失败次数超过 N 次（如5次）后，账户临时锁定 X 分钟，并记录日志
  - 锁定触发时自动通知管理员（通过邮件或系统内通知）
  - 可选启用 reCAPTCHA 防止暴力破解

## 页面 A2 — 注册 / 创建账户

- **角色**：尚无账户的新用户
- **核心元素**：
  - 注册表单字段：
    - 用户名（实时校验是否已被占用）
    - 邮箱地址（实时校验格式）
    - 密码（显示强度指示器）
    - 确认密码（实时校验是否匹配）
  - 注册按钮（主按钮样式，初始状态置灰直至表单通过校验）
  - 可选的“发送验证码”按钮（若启用邮箱/手机验证）
  - 服务条款和隐私政策勾选框（必选）
  - 页面底部的“已有账户？**登录**”链接（返回页面 A1）
  - 页面顶部的语言切换器（与登录页保持一致）
- **交互逻辑**：
  - **实时校验**：
    - 用户名输入时异步请求后端验证是否已被占用
    - 邮箱输入时验证格式合法性
    - 密码输入时实时显示强度（弱/中/强）
    - 确认密码失焦时校验是否与密码一致
  - **表单提交**：
    - 所有校验通过且勾选条款后，注册按钮变为可点击状态
    - 点击注册提交表单
    - 若启用邮件验证：显示“激活邮件已发送至您的邮箱，请查收并完成激活”
    - 若无需验证：注册成功后自动登录并跳转到 Dashboard，或跳转到登录页并提示“注册成功，请登录”
  - **验证码流程**（可选）：
    - 点击“发送验证码”按钮，按钮进入倒计时状态（60秒）
    - 输入验证码后实时校验
- **安全策略**：
  - 密码复杂度要求：至少8位，包含大小写字母、数字、特殊字符
  - 启用 reCAPTCHA 防止机器人批量注册
  - 注册频率限制：同一IP或邮箱在24小时内注册次数限制
  - 注册成功后触发欢迎邮件（系统通知）
  - 所有注册尝试记录日志，异常行为触发告警


## 页面 B — 系统仪表盘（Dashboard）

* 角色：管理员 / PM / Doc Admin
* 显示项：系统健康（向量 DB 状态、LLM 可用性）、最近导入任务、今日 token 消耗、告警条目。
* 交互：点击导入任务可跳转到任务详情；点击告警可打开告警详情并执行“回溯/限流”动作（管理员权限）。
* 后端调用：`GET /api/v1/admin/metrics`、`GET /api/v1/documents/import/recent`。

## 页面 C — 文档导入向导（Import Wizard）

* 步骤 1：选择来源（Upload / URL / S3 / Confluence）

  * 校验：若选择文件上传，限制单文件最大 200MB（MVP 可限制更小）。
* 步骤 2：填写 metadata（dept, sensitivity, tags, version）— 必填项包含 sensitivity。
* 步骤 3：Chunk 配置（显示默认 chunk size，可选覆盖）
* 步骤 4：Review & Submit → 调用 `POST /api/v1/documents/import`（返回 taskId）
* 上传后流程：自动进入导入队列并在前端显示 task 状态（poll `GET /api/v1/documents/import/{taskId}`，失败重试 3 次并在 UI 提示“解析异常，查看日志”）。
* 错误场景：解析失败→显示失败原因（解析错误/ OCR 失败/ 超时）并允许用户下载解析日志。

## 页面 D — 检索与 QA（主工作区）

* 顶部：查询输入框（支持自然语言与关键字切换）
* 侧栏：filter（部门、文档类型、时间范围、sensitivity）、search history、saved queries
* 中央：结果区域，分两列

  * 左栏：AI 回答卡片（显示 answer， confidence（估算）， actions：Save Draft / Create Requirement / Report Issue）
  * 右栏：来源面板（sources 列表，逐条显示 doc title、excerpt、score、open 原文按钮）
* 交互流程：

  1. 用户输入 query，点击搜索 → 前端发送 `POST /api/v1/qa`（带 userId & filters）
  2. 显示 loading spinner（并显示 estimated cost 若启用预览）
  3. 展示回答；用户可点击“查看来源”展开每个 chunk 的上下文片段并跳转到文档原文（在新 tab，权限控制生效）
  4. 用户可将回答“保存为草稿”或“直接生成需求草稿”（跳转至生成页面并预填内容）
* 错误与降级：若 LLM 调用失败，UI 展示“降级结果：仅返回检索片段”并在顶部横幅解释。

**可访问性与可用性细节**

* 回答卡片支持折叠/展开，支持高亮检索到的关键词。
* Sources 支持按 score/recency 切换排序。
* 在敏感文档过滤时，若用户无权访问，右栏显示“已过滤的来源（若需访问请申请）”。

## 页面 E — 生成/编辑需求草稿（Draft Editor）

* 元素：标题、模板选择下拉、字段表单（基于模板变量自动生成）、富文本编辑器（支持 Markdown/HTML）、source pane（显示用于生成的 sources），版本历史侧边栏。
* 交互逻辑：

  * 初次进入若来自 QA 页面（“生成草稿”），预填生成文本与 sources。
  * 编辑实时保存草稿（自动保存到 Draft 表，节流 2s）。
  * 用户可点击“提交审批”：前端调用 `POST /api/v1/drafts/{id}/submit`（须选 approver 列表），并显示审批状态。
  * 若草稿包含敏感信息（由 PII 检测模块标注），弹窗提示并强制用户确认。
* 校验：标题非空，至少包含 Overview 与 Acceptance Criteria（模板约束）。
* 协作：支持 @mention 审批人并发送通知（邮件/企业 IM）。

## 页面 F — 审批队列（Approvals）

* 列表：待审批草稿（优先级、提交人、提交时间）
* 审批详情页：显示草稿全文、生成 sources、版本 diff、历史对话、审批按钮（Approve / Reject / Request Changes），可附注评审意见。
* 审批规则：若草稿修改涉及 high-sensitivity 内容，审批流程自动加入安全审核人。
* 审批结果：Approve → 写回（调用外部系统写回 API，需二次确认）；Reject → 返回编辑者并记录原因。

## 页面 G — 管理控制台（Admin）

* 功能：模板管理、导入任务管理、向量 DB 状态、模型配置（provider 切换/keys 管理）、预算与告警配置、审计查询。
* 交互逻辑：变更模型或 API Key 需管理员权限并留下变更记录；更改敏感等级策略需二次确认（安全复核）。
* 后端调用示例：`GET /api/v1/admin/templates`、`POST /api/v1/admin/models/switch`（带变更记录）。

---

# 9 前端实现细节（交互与状态机）

* 所有关键异步请求应使用 trace-id（uuid）并展示在 UI 显眼位置，便于排查。
* 导入任务使用后台任务队列（后端）并通过 WebSocket/Server-Sent-Events 推送任务状态到前端（避免频繁轮询）。前端状态机：`idle → uploading → parsing → embedding → indexing → done | failed`。
* 编辑器自动保存采用乐观并行控制（Optimistic Concurrency）：每次保存带 `editorVersion`，写入失败提示用户合并冲突。
* 审批按钮需做幂等处理并展示最终状态（避免重复提交）。
* 长耗时动作（bulk ingestion, mass reindex）应在 UI 显示进度条并允许取消。

---

# 10 安全、合规与隐私控制（实施细则）

* **数据分级**：在 ingestion 时强制选择 `sensitivity`，系统不允许匿名导入高敏感文档。
* **ACL 强制**：检索时在 vector 查询后执行 metadata-level filter（后端强制执行，不依赖前端）。
* **最小化上下文传输**：发送给外部 LLM 的上下文仅包含必要 chunk，且对敏感字段做 redaction。
* **审计**：每次 LLM 请求记录 `promptHash`（不保存明文 prompt 或对其做加密保存，根据公司合规策略决定）。
* **Secrets 管理**：所有 provider key 存于 Vault（或云 KMS），前端/后端不直接暴露。
* **Prompt Injection 防护**：对用户输入做白名单与模板结构化，避免将未审核用户输入直接拼接为 system prompt。
* **数据删除**：提供数据擦除 API（遵循 GDPR 风险）：删除文档并从向量 DB 中删除相应向量，删除后生成不可逆的删除记录。

---

# 11 部署、监控与运维要点

* 部署：Kubernetes（Deployment + StatefulSet for vector DB） + Helm。向量 DB 可设冷热层；对象存储使用 S3 或兼容存储。
* CI/CD：代码通过 PR → 自动测试 → Canary 部署 → 逐步放量。
* 监控指标（必须实现）：embedding QPS、vector query latency p50/p95/p99、LLM token/day、ingestion throughput、failed_jobs_rate。告警：vector db down / token cost exceed / abnormal ingestion failure rate。
* 灾备：向量 DB 做定期 snapshot；对象存储版本化。

---

# 12 验收标准与里程碑（建议）

**里程碑**

* PoC（2 周）：完成基础文档导入与 QA，展示 Demo。
* Beta（6 周）：加入生成草稿、审批流程、基础审计与权限。
* 生产（12 周）：K8s 部署、监控、合规评估、内部培训。

**验收点（生产发布前）**

1. 功能：MVP 列表全部通过集成测试与安全评估。
2. 性能：在 50 并发检索下，P95 latency ≤ 3s（不含外部 LLM）。
3. 安全：敏感文档测试通过（无越权检索）。
4. 成本：提交 30 天 token 与 embedding 预算模型，预算告警与限流策略有效。

---

# 13 风险与缓解措施

* 风险：LLM 产生幻觉 → 缓解：强制返回 sources + 限制生成仅基于上下文；对关键业务回答加入人工复核。
* 风险：数据泄露 → 缓解：最小化上下文、DPA 合同、私有化模型（高合规客户）。
* 风险：成本暴涨 → 缓解：预算告警、低优先级请求降级为仅检索模式、批量 embedding 优化。
* 风险：向量 DB 性能瓶颈 → 缓解：冷热数据分层、索引调优（HNSW/IVF）、读写分离、使用高性能替代（Milvus/Pinecone）。

---
