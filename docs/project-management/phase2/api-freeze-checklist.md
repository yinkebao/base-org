# 核心接口口径冻结清单（Phase2）

本清单用于冻结认证、文档导入、文档管理、QA 及管理端的 API 口径，将每个接口的 method/path/参数/响应/鉴权/分页/错误码/状态明确写出，在后续迭代中作为唯一对齐基线。关键风险项（诸如文档树 GET/POST、QA history、SearchResult 字段、401+统一错误结构）在相关模块中显式标注，并在尾部列出“待负责人裁决事项”。

## 认证模块
| 接口 | 方法 | 路径 | 请求参数 | 响应字段 | 鉴权 | 分页 | 错误码 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 用户登录 | POST | `/api/v1/auth/login` | `LoginRequest(username,password)` 通过 `@RequestBody`，`src/js/services/auth-service.js` 的 `AuthService.login` 发送 | `ApiResponse<AuthResponse>` 包含 `token`, `user{id, name, dept}`，前端 `session-service` 持久化 | POST 需 Authorization header 由后端返回，前端保存到 `session-service` | 无 | `AUTH_INVALID_CREDENTIALS`、`AUTH_LOCKED`、`AUTH_TOKEN_EXPIRED` 等（见 `docs/API_CONTRACT.md`） | 冻结（后端 `AuthController.login` 与前端 `AuthService.login` 已对齐，需联调确认返回字段完全匹配） |
| 用户注册 | POST | `/api/v1/auth/register` | `RegisterRequest`（用户名/密码/昵称等），由 `AuthService.register` 触发 | 同上 | 无 | 无 | `USERNAME_TAKEN`、`PASSWORD_WEAK`、`AUTH_NOT_ACTIVATED` | 冻结 |
| 用户名检查 | GET | `/api/v1/auth/check-username` | query `username` | `ApiResponse<CheckUsernameResponse>` | 无 | 无 | `USERNAME_TAKEN` | 冻结 |
| SSO 登录启动 | GET | `/api/v1/auth/sso/start` | query `provider`（由 `AuthService.startSso` 发起） | `ApiResponse<SsoStartResponse>`（含 `authUrl`） | 无 | 无 | `AUTH_UNAUTHORIZED` | 待确认（控制器存在，需进一步冻结返回结构及 provider 列表） |

## 文档导入模块
| 接口 | 方法 | 路径 | 请求参数 | 响应字段 | 鉴权 | 分页 | 错误码 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 创建导入任务 | POST | `/api/v1/documents/import` | `MultipartFile file` + `sensitivity`/`parentId`/`description`（通过 `@RequestParam`，前端通过 `ImportService.createImportTask` 的 `FormData` 触发） | `ApiResponse<ImportTaskResponse>` 包括 `taskId/status/progress` | `@AuthenticationPrincipal` 必须登录 | 无 | `IMPORT_FILE_TOO_LARGE`, `IMPORT_PARSE_ERROR`, `IMPORT_TASK_FINALIZED` | 冻结（前端 `ImportService.createImportTask` 使用 `FormData`，需确认请求头被正确处理） |
| 查询任务状态 | GET | `/api/v1/documents/import/{taskId}` | path `taskId`（`ImportService.getImportTaskStatus`） | `ApiResponse<ImportTaskResponse>` | `@AuthenticationPrincipal` | 无 | `IMPORT_TASK_NOT_FOUND`, `IMPORT_TASK_FINALIZED` | 冻结 |
| 取消任务 | POST | `/api/v1/documents/import/{taskId}/cancel` | path `taskId`（`ImportService.cancelImportTask`） | 同上 | `@AuthenticationPrincipal` | 无 | `IMPORT_TASK_FINALIZED` | 冻结 |
| 获取最近导入 | GET | `/api/v1/documents/import/recent` | query `page`, `size`（`ImportService.getRecentImports`、`DashboardService.getRecentImports` 均调用） | `ApiResponse<RecentImportsResponse>` 包含 `tasks`, `total`, `page`, `size` | `@AuthenticationPrincipal` | page/size | `IMPORT_TASK_NOT_FOUND`（当页空） | 冻结（需确认 `dashboard` 与 `imports` 两端返回数据一致） |

## 文档树 / 列表 / 详情 / 编辑
| 接口 | 方法 | 路径 | 请求参数 | 响应字段 | 鉴权 | 分页 | 错误码 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 文档树 | GET | `/api/v1/documents/tree` | `parentId`（可选 query）+`@AuthenticationPrincipal`（前端 `DocumentService.getDocumentTree` 目前用 POST body 并传 `dept`） | `ApiResponse<DocumentTreeNode>`（含 `children`） | 必须登录 | 无 | `DOC_NOT_FOUND`、`DOC_ACCESS_DENIED` | 阻塞（前端 `DocumentService.getDocumentTree` 通过 `POST` body 传 `dept`，调用方式与控制器 `GET` 不一致，需统一） |
| 文档列表 | GET | `/api/v1/documents` | query `parentId`, `page`, `size`, `status`, `keyword`（前端 `DocumentService.getDocumentList` POST body 包含分页/筛选） | `ApiResponse<DocumentListResponse>`（`documents`, `total`, `page`, `size`） | 必须登录 | `page/size` + status/keyword | `DOC_ACCESS_DENIED` | 阻塞（同上，前端 POST，需确认分页字段名称及是否返回 `total`/`page`/`size`） |
| 文档详情 | GET | `/api/v1/documents/{docId}` | path `docId`（`DocumentService.getDocumentDetail` 发起） | `ApiResponse<DocumentDetailResponse>`（包括 `chunkCount`, `contentMarkdown` 等） | 必须登录 | 无 | `DOC_NOT_FOUND`, `DOC_ACCESS_DENIED` | 冻结 |
| 文档更新 | PUT | `/api/v1/documents/{docId}` | body `UpdateDocumentRequest(title,contentMarkdown,version)`（`DocumentService.updateDocument`） | 同 `DocumentDetailResponse` | 必须登录 | 无 | `DOC_VERSION_CONFLICT` | 冻结 |
| 文档图片上传 | POST | `/api/v1/documents/{docId}/assets/images` | `FormData(image,alt)`（`DocumentService.uploadDocumentImage`） | `ApiResponse<DocumentImageUploadResponse>`（`url`,`filename`,`size`） | 必须登录 | 无 | `DOC_IMAGE_INVALID_TYPE`, `DOC_IMAGE_TOO_LARGE` | 冻结（FormData 需确认 `Content-Type` 处理） |
| 图片访问 | GET | `/api/v1/documents/{docId}/assets/images/{filename}` | path `docId`, `filename` | `ResponseEntity<Resource>` | 必须登录 | 无 | `DOC_ACCESS_DENIED` | 冻结（需明确前端是否需携带 Authorization 或使用 signed URL） |
| 图文创建（暂未线上） | POST | `/api/v1/documents` | `folderId` + payload | `ApiResponse<DocumentDetailResponse>` | 必须登录 | 无 | `DOC_ACCESS_DENIED` | 待确认（目前前端 `createDocument` 使用 `/documents` POST），需确认是否要作为冻结接口 |

## QA（问答）模块
| 接口 | 方法 | 路径 | 请求参数 | 响应字段 | 鉴权 | 分页 | 错误码 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 智能问答 | POST | `/api/v1/qa` | `QARequest(question,topK,scoreThreshold,includeSources,stream)`（前端 `src/js/services/qa-service.js` `query` 函数调用） | `ApiResponse<QAResponse>`（`answer/confidence/sources/processingTimeMs`） | 必须登录 | 无 | `QA_QUERY_EMPTY`, `QA_NO_RESULTS`, `QA_LLM_TIMEOUT`, `QA_RATE_LIMITED`, `QA_SENSITIVE_FILTERED` | 冻结（需确定返回的 `QAResponse.SourceChunk` 结构满足前端在 `qa-service.js` 中映射 `chunkId/docId/docTitle/content/score/metadata`） |
| QA history | GET | `/api/v1/qa/history` | query `page`, `size`（`qa-service.getHistory` 传递该参数） | 期待 `ApiResponse` 包含 `records`, `total` 等 | 必须登录 | page/size | `QA_RATE_LIMITED`? | 阻塞（后端目前无对应 Controller，是否纳入本期需裁定） |
| SearchResult 字段 | —— | `/api/v1/qa` 响应内`sources` | 前端 `qa-service` 映射 `content`, `docTitle`, `score`, `chunkId`, `docId`, `metadata.dept/sensitivity` | `QAResponse.SourceChunk` 目前仅 `chunkId/docId/docTitle/content/score/chunkIndex` | —— | —— | —— | 阻塞（需冻结 `sources` 字段名与类型，并决定是否返回 `sectionTitle/metadata`） |

## 管理端指标与监控
| 接口 | 方法 | 路径 | 请求参数 | 响应字段 | 鉴权 | 分页 | 错误码 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 系统指标 | GET | `/api/v1/admin/metrics` | 无（`DashboardService.getMetrics` 触发） | `ApiResponse<MetricsResponse>`（`documents`,`imports`,`storage`,`system`,`timestamp`） | 需登录或管理角色 | 无 | `INTERNAL_ERROR` | 冻结（需联调校验 `documents.byType` / `storage.usedSpace` 等字段存在） |
| 系统告警 | GET | `/api/v1/admin/alerts` | 无（`DashboardService.getAlerts` 触发） | `ApiResponse<AlertsResponse>`（告警列表+分页） | 需登录或管理角色 | page/size | `INTERNAL_ERROR` | 冻结 |
| 健康检查 | GET | `/api/v1/admin/health` | 无 | `HealthResponse`（`status/database/storage` 等） | 需登录 | 无 | `INTERNAL_ERROR` | 冻结（仅运维调用，前端暂未集成） |

## 错误码与 401 统一处理
- **结构**：所有 `Controller`（如 `AuthController`, `DocumentController`, `QAController`）封装成 `ApiResponse`（`docs/API_CONTRACT.md` 中 `code/message/data`），前端 `src/js/services/http-client.js` 只读取 `code/message` 并在 `response.ok` 为 false 时呈现；需确认 `ApiResponse` 始终设置 `code` 字段并匹配文档。
- **401 失效**：`http-client` 检测 `response.status === 401` 即刻 `clearSession()`，但未记录 `traceId`; 需要冻结行为：后端在 `GlobalExceptionHandler` 返回 `AUTH_UNAUTHORIZED`，前端统一展现“登录已过期”，并触发刷新/跳转。
- **建议**：冻结时增加 `docs/API_CONTRACT.md` 中 `ERROR CODE` 表列表；联调用真实错误码（如 `DOC_ACCESS_DENIED`）验证前端提示逻辑。

## 待负责人裁决事项
- 文档树（`/documents/tree`）与列表（`/documents`）是否改为前端当前的 POST 方式，还是后端保持 GET，并在前端改为 GET（影响分页与 `dept` 传参）。  
- `/api/v1/qa/history` 是否纳入本期冻结口径，如是则需要新增控制器+DTO；如否需明确告知前端去除调用。  
- QA `sources` 字段最终冻结结构（是否保留 `metadata`、`sectionTitle`、返回 `content`/`text` 的标准名）以及默认值填充逻辑。  
- 401 + 业务错误码前端处理方式是否沿用 `http-client` 现有结构，或需扩展 `ApiResponse` 解析层以支持 `code` 级别分支。  
