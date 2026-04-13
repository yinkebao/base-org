# 后端阶段二实施包拆解

## 实施包清单
1. QA 返回结构收口
2. 审计日志埋点与查询链路
3. 对象存储扩展与统一接口
4. 导入任务调度与恢复能力
5. 监控告警与指标联动
6. 认证与 JWT 安全加固

## 每个实施包的目标、涉及模块、依赖、风险、验收标准

### 1. QA 返回结构收口
- 目标：统一 `/api/v1/qa` 在正常/降级/错误三种场景下的响应字段与格式，确保 `includeSources`、`confidence`、`degraded` 以及 metadata 一致，配合前端展示。  
- 涉及模块：`controller/QAController.java`、`service/QAService.java`、`dto/qa/QARequest.java`、`dto/qa/QAResponse.java`。  
- 依赖：需确认联调的前端预期字段、降级 display 文案；若后台拟引入 `QAResponse.SourceChunk` 字段变化，也需前端同步。  
- 风险：问答链路在向量检索失败时仅依赖降级文本，未记录 `errorCode`，需防止前端误判；`VectorSearchService` 异步调用可能导致 `processingTimeMs` 不准确。  
- 验收标准：前端测试覆盖常规问答/为空/LLM失败三个情景，接口返回字段完全匹配合约（包括`confidence`=0,`degraded`=true且`degradeReason`有值）；文档中新增接口规范说明。

### 2. 审计日志埋点与查询链路
- 目标：在问答、文档导入、LLM 调用等主流程写入 `AuditLog`，并在 `/api/v1/admin/metrics` 或独立接口中暴露统计。  
- 涉及模块：`entity/AuditLog.java`、`mapper/AuditLogMapper.java`、`service/QAService.java`、`service/ImportService.java`、`service/rag/LLMService.java`、`controller/admin/DashboardController.java`、可能新增 `service/AuditLogService.java`。  
- 依赖：需等待审计数据结构与 admin 面板字段的联调确认（例如 `promptHash` 如何生成、tokenCost 计算方式）；与监控实施包共享告警字段。  
- 风险：tokenCost 依赖 OpenAI 响应，若未统一调用策略会导致数据不一致；审计写入量大需评估数据库写入性能。  
- 验收：每个 QA 请求/导入任务必须插入一条审计记录；`AuditLogMapper.countByAction` 和 `sumTokenCost` 接口在 admin metrics 中可被查询，且可根据 `/api/v1/admin/metrics` 返回 `auditSummaries` 栏位。

### 3. 对象存储扩展与统一接口
- 目标：支持本地与 S3 兼容对象存储，提供统一 `StorageService` 接口给 `ImportService`、`DocumentService`、以及静态资源访问。  
- 涉及模块：`app.storage` 配置（`resources/application.yml`）、`service/ImportService.java`、`service/DocumentService.java`、新建接口如 `storage/StorageService` + 实现 `LocalStorageService` 与 `S3StorageService`。  
- 依赖：需联调确认 S3 兼容 endpoint、签名策略、Bucket 名称与权限；若前端访问路径变更，需同步 `DocumentController` 返回的 asset URL。  
- 风险：迁移到 S3 需考虑已有本地文件的迁移路径与权限；若访问路径由本地 `storagePath` 变成 `/api/v1/storage/...` 需防止缓存穿透。  
- 验收：导入任务生成的 `filePath` 可根据配置写入本地或 S3，并且文档图片上传/下载命令可正常返回 URL；`storage` 接口支持切换配置不重启即可切换对象存储类型。

### 4. 导入任务调度与恢复能力
- 目标：增强 `ImportService.processTask` 的可观测与恢复能力，提供重试、错误消息与导入日志，可通过 `/api/v1/documents/import/{taskId}` 查询详细状态。  
- 涉及模块：`service/ImportService.java`、`mapper/ImportTaskMapper.java`、`entity/ImportTask.java`、`dto/importtask` 包、`controller/ImportController.java`，可能新增 `importtask/ImportTaskLog`。  
- 依赖：需与队列/线程池配置组件同步（如 `@EnableAsync` 的 Executor 配置）；若计划引入任务队列需联调基础设施（Redis、RabbitMQ）。  
- 风险：异步任务默认线程池数量对大文件处理不足，容易导致队列积压；任务中抛异常可能未及时写入 `ImportTask.TaskStatus.FAILED`，需确保 `markTaskFailed` 覆盖所有异常。  
- 验收：上传文档后可在 `/api/v1/documents/import/{taskId}` 看到分阶段进度（解析/分块/存储/完成），`cancel` 接口仅在 Pending 状态成功；后台日志记录每次状态变更和 `errorMessage`，并可通过 `RecentImportsResponse` 分页查看。

### 5. 监控告警与指标联动
- 目标：补全 `DashboardService` 的告警规则与数据点，将 `metrics/alerts/health` 接口与 Prometheus/Grafana 规则对齐，并补充 UI 所需的告警级别字段。  
- 涉及模块：`service/DashboardService.java`、`dto/dashboard/*`、`controller/admin/DashboardController.java`，配合 `service/rag` 的 `VectorSearchService` 与 `LLMService` 在异常时上报指标。  
- 依赖：需与监控联调工程师确认告警阈值（如磁盘使用率、LLM 降级率、导入失败率）以及指标导出路径（Prometheus scrape、Alertmanager webhook）。  
- 风险：未对外暴露 Prometheus endpoint 可能导致告警收敛；`AlertsResponse` 中 `type` 与 `severity` 归类需统一到前端。  
- 验收：`/api/v1/admin/alerts` 能返回按 `type`/`description` 分类的告警列表，且 `DashboardService` 可提供磁盘、导入、QA 异常三类数据供前端渲染前端告警面板；监控系统能根据这些指标触发通知。

### 6. 认证与 JWT 安全加固
- 目标：强化密钥管理、token 失效/刷新机制和角色规则，确保默认 `jwt.secret` 必须通过环境变量覆盖，并可配置 token 黑名单或短生命周期策略。  
- 涉及模块：`resources/application.yml`、`security/JwtUtil.java`、`config/SecurityConfig.java`、可能新增 `security/JwtRevocationService.java` 与 `auth/AuthController`。  
- 依赖：与安全团队联调确定密钥部署策略、refresh token 方案与角色扩展；若引入 `HashiCorp Vault` 需与 Secrets 管理组同步。  
- 风险：未轮换密钥可能导致泄露后风险；token 黑名单未实现时无法即时禁用用户；`JwtAuthenticationFilter` 依赖 `Authorization` header，若前端缓存旧 token 需配合刷新流程。  
- 验收：部署环境中 `JWT_SECRET` 必须存在且不等于默认值；后端在 `JwtUtil` 中提供 `getExpiration` 等暴露给前端；`SecurityConfig` 能根据 `ROLE_ADMIN`/`ROLE_USER` 控制访问，且默认路径已受保护。

## 推荐执行顺序
1. 认证与 JWT 安全加固（安全为先、无前置依赖）
2. 审计日志埋点与查询链路（可在安全加固后同步，依赖已完成的 QA/导入链路）
3. QA 返回结构收口（需要审计与认证结果配合字段）
4. 导入任务调度与恢复（增强现有流程，应在审计与对象存储基础上完善）
5. 对象存储扩展与统一接口（依赖导入与文档任务完成后才能大规模迁移）
6. 监控告警与指标联动（最后整合前述数据，联调告警规则）

## 联调与可先行项
- 需要联调确认：QA 返回字段、审计 `promptHash` 生成、对象存储 S3 endpoint、监控告警阈值、JWT 密钥策略。  
- 可先行实施：认证安全初步加固（本地 fallback）、审计写入框架搭建、导入任务状态增强（独立日志），在确认联调结果前先完成内部逻辑设计与单元测试。
