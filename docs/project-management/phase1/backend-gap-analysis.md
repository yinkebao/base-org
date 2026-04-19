# 后端阶段一现状与差距分析

## 当前已完成能力
- Spring Boot 主应用已经载入异步与 MyBatis 配置（`backend/src/main/java/com/baseorg/docassistant/DocAssistantApplication.java`），并由 `config/SecurityConfig.java` 与 `security/*` 实现 JWT 认证、角色过滤与公开端点，覆盖登录、注册、SSO 和 Swagger/OpenAPI 路径。
- QA/RAG 核心链路成型：`controller/QAController.java` 提供 `/api/v1/qa`，`service/QAService.java` 负责调度向量检索（`service/rag/VectorSearchService.java`）与 LLM 生成（`service/rag/LLMService.java`），通过 `config/AiProviderConfig.java` + `service/rag/SpringAiRuntime.java` 注入 Spring AI ChatClient 与 EmbeddingModel，配套 `dto/qa` 定义请求/响应结构。
- 文档导入与管理覆写全链：`controller/ImportController.java` 暴露创建/查询/取消/最近任务接口，`service/ImportService.java` 提供上传保存、解析（`service/parser/*`）、分块（`service/chunk/SmartChunkSplitter.java`）与任务状态更新逻辑；`DocumentService.java` 承担文档树、列表、详情、面包屑与图片上传，均以 `app.storage.local.path`（`resources/application.yml`）为实际存储位置。
- 向量数据与文档模型完成：`entity/Chunk.java`、`entity/Document.java` 以及对应的 Mapper 已定义结构，支持 pgvector 字段与 metadata，其中 `VectorSearchService` 中的 SQL 查询配合 `mapper.DocumentMapper` 获取标题、`scoreThreshold` 过滤结果。
- 审计基础表与查询支持：`entity/AuditLog.java` 定义 `promptHash`/`tokenCost`/`traceId` 等字段，`mapper/AuditLogMapper.java` 提供按用户、按动作统计的查询，数据可供后续审计与指标使用。
- 监控能力拥有 REST 入口：`controller/admin/DashboardController.java` 通过 `/api/v1/admin/{metrics,alerts,health}` 与 `DashboardService.java` 中的文档、导入、存储与系统资源指标（包括存储容量、CPU/内存、告警逻辑）完成对外报告。

## 当前未完成能力
- 审计写入链路尚未接入业务：虽然存在 `AuditLog` 实体与 Mapper，但后端核心流程（问答、导入、LLM 调用）中未见对 `AuditLogMapper`/插入操作的调用，无法保证 `promptHash` 等字段落地与 365 天保存策略一致。
- 对象存储仍局限本地：`ImportService` 与 `DocumentService` 均读取 `app.storage.local.path`（`resources/application.yml`），未提供 `app.storage.s3` 或其它 S3 兼容实现，文档上传、导入文件与文档图片均只能落地在磁盘，无法满足云存储要求。
- AI 服务容错与指标薄弱：`LLMService.generate` 只在 `AiRuntime.chatAvailable()` 为 true 时调用，缺少调用超时、重试、降级或 token 记录逻辑，`SmartChunkSplitter` 与 `VectorSearchService` 也没有显式的调用链审计；`AppAiProperties` 仅支持 `OPENAI_COMPATIBLE/DASHSCOPE`，但没有 runtime fallback 策略。
- 导入任务可视化与异常恢复有限：`ImportService.processTask` 通过 `@Async` 处理但没有明确的队列或调度配置（依赖默认线程池），也没有对外提供任务日志或重试机制，`dashboard` 的导入平均耗时写死为 2.5 秒，缺少真实观测数据。

## 高优先级风险
- 向量检索 SQL 的字符串拼接：`VectorSearchService.vectorSearch` 直接把 `request.getTopK`、敏感度列表与向量字符串拼接进 SQL，若未严格控制参数或 `sensitivityLevels` 来自前端，可能引入 SQL 注入与性能抖动，需要改用 `Spring JdbcTemplate` 的参数化查询和 `pgvector` 扩展类型。
- LLM 运行时失效就无降级：`LLMService.generate` 只凭 `AiRuntime.chatAvailable()` 检查，若 OpenAI 兼容服务不可达，`QAService` 仅返回固定降级文案（`buildFallbackAnswer`），没有记录 `promptHash`、token 消耗或调用超时，审计与计费能力不足。
- JWT 密钥默认值与滚动机制缺失：`application.yml` 中 `jwt.secret` 默认 `your-256-bit-secret-key-here...`，若部署未设置环境变量即使用明文密钥，且 `JwtUtil` 未提供密钥轮换与失效策略，存在被泄露后无法快速切断风险。
- 存储容量与监控数据不完整：`DashboardService` 依靠文件系统统计（`storagePath`）与 JVM 报表，未见与 Prometheus/Grafana 的 Exporter 或外部指标适配，如果存储路径被删盘或空间达 100%，缺乏主动告警链路。

## 依赖联调确认的接口问题
- 问答接口：`/api/v1/qa`（`controller/QAController.java`）需要与前端确定请求字段 `topK`、`scoreThreshold`、`includeSources` 的默认值与错误响应格式，特别是在降级流程编排时要对齐文案与状态码。 
- 文档导入：`/api/v1/documents/import` 系列端点（`ImportController.java`）上传 `file`/`sensitivity`/`parentId`，返回的 `ImportTaskResponse` 格式需与前端导入页保持一致，同时联调确认取消 (`POST /{taskId}/cancel`) 的生效时机与后台任务状态。 
- 文档管理：`/api/v1/documents`、`/api/v1/documents/tree`/`/{id}`/`PUT`（`DocumentController.java` + `DocumentService.java`）依赖前端传递 `parentId`、`status`，并且图片上传（`/documents/{id}/assets/images`）当前仅支持本地路径，需与客户端同步 URL 路径与鉴权方式。 
- 审计、告警与健康：`/api/v1/admin/{metrics,alerts,health}`（`controller/admin/DashboardController.java`）在没有持续推送机制下只能被轮询，需与前端约定刷新频率并确认后端响应字段、告警级别分类（`AlertsResponse`）。

## 第二阶段建议任务拆解（按优先级）
1. 打通审计写入链路：在 QA、导入、LLM 调用点引入 `AuditLogMapper`（参照 `entity/AuditLog.java`/`mapper/AuditLogMapper.java`），确保 `promptHash`、`tokenCost`、`traceId` 等字段按业务场景写入、并由新接口供审计/管理面板查询。 
2. 扩展对象存储能力：在 `app.storage` 下新增 S3 配置，并在 `ImportService.saveFile`、`DocumentService.uploadDocumentImage` 等路径引入可插拔的存储服务，目标支持本地与 S3 双通道，资产 URL 返回统一的 `/api/v1/storage/...` 访问地址。 
3. 加强 AI/RAG 故障与指标：在 `service/rag` 增加 token 计数、超时与重试逻辑（一个切面或 Filter 记录 `promptHash` 并落到审计表）；同时将 `VectorSearchService` 的 pgvector 查询改为参数化 + 引入 `RequestMetrics`，为后续 Prometheus 导出埋点。 
4. 完善导入任务调度与状态暴露：引入专用任务队列或 `ThreadPoolTaskExecutor` (更细粒度配置)，新增导入当次日志表和 `ImportTask` 的 `errorMessage`/`retries` 字段，前端通过 `/recent` 与 `/tasks/{id}` 获取更多诊断信息。 
5. 监控与告警联调：将 `DashboardService` 结果与 Prometheus/Grafana Exporter 对接，补充 `AlertsResponse` 触发条件（如磁盘使用 > 80%、LLM 降级率高于阈值），并联合联调工程师确定告警级别对应 UI 行为。 

