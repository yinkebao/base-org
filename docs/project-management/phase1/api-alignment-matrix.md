# 核心接口对齐矩阵（Phase1）

本矩阵以认证、文档导入、文档查询/编辑、QA、管理端为最小覆盖模块，聚焦前端调用位置、后端控制器位置、当前状态、差异与建议动作，并在末尾列出跨模块的关键风险项。

## 认证
| 接口 | 方法 | 路径 | 前端调用位置 | 后端控制器位置 | 当前状态 | 主要差异 | 建议动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 用户登录 | POST | `/api/v1/auth/login` | `src/js/services/auth-service.js`（`AuthService.login`） | `backend/src/main/java/com/baseorg/docassistant/controller/AuthController.java`（`login`） | 已对齐 | 响应结构遵循 `docs/API_CONTRACT.md`，需确认后端返回 token/user 结构与前端 `session-service` 结构完全一致 | 联调时带上实际登录响应，确认 `Authorization` token、`user.id`、`expiresAt` 等字段与前端存储匹配 |
| 用户注册 | POST | `/api/v1/auth/register` | `src/js/services/auth-service.js`（`register`） | `.../AuthController.java`（`register`） | 已对齐 | 同上 | 验证异常码（`USERNAME_TAKEN` 等）在前端可定位 |
| 用户名检查 | GET | `/api/v1/auth/check-username` | `src/js/services/auth-service.js`（`checkUsername`） | `.../AuthController.java`（`checkUsername`） | 已对齐 | None | 确认 query 参数命名一致 |
| SSO 登录启动 | GET | `/api/v1/auth/sso/start` | `src/js/services/auth-service.js`（`startSso`） | `.../AuthController.java`（`startSso`） | 部分对齐 | 文档中已列出接口但后端需确认某些 provider 不同的返回字段 | 联调时比对 `Authorization` 重定向 URL 与前端期望的结构，文件 `docs/API_CONTRACT.md` 需补全返回示例 |

## 文档导入
| 接口 | 方法 | 路径 | 前端调用位置 | 后端控制器位置 | 当前状态 | 主要差异 | 建议动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 创建导入任务 | POST | `/api/v1/documents/import` | `src/js/services/import-service.js`（`createImportTask`） | `backend/.../ImportController.java`（`createTask`） | 已对齐 | 前端使用 `FormData` 但 controller 接受 `MultipartFile`，需确认前端 `request` 允许自动设置 `Content-Type` （`FormData` 模式会删除默认 header） | 感知接口附件大小限制与返回字段（`taskId/status`），补齐文档 |
| 查询任务状态 | GET | `/api/v1/documents/import/{taskId}` | `src/js/services/import-service.js`（`getImportTaskStatus`） | `.../ImportController.java`（`getTaskStatus`） | 已对齐 | None | 确认 `taskId` 格式与分页/权限一致 |
| 取消任务 | POST | `/api/v1/documents/import/{taskId}/cancel` | `src/js/services/import-service.js`（`cancelImportTask`） | `.../ImportController.java`（`cancelTask`） | 已对齐 | None | 确认 `POST` 可短路重复请求 |
| 最近导入列表 | GET | `/api/v1/documents/import/recent` | `src/js/services/import-service.js`（`getRecentImports`） & `src/js/services/dashboard-service.js`（`getRecentImports`） | `.../ImportController.java`（`getRecentImports`） | 已对齐 | 前端默认 `page=0/size=10`，需确认后端响应分页 metadata (`totalElements`) | 联调步骤：分别调用 `dashboard` 与 `import` 接口确认数据一致性 |

## 文档查询与编辑
| 接口 | 方法 | 路径 | 前端调用位置 | 后端控制器位置 | 当前状态 | 主要差异 | 建议动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 文档树 | GET | `/api/v1/documents/tree` | `src/js/services/document-service.js`（`getDocumentTree`，`POST` body 包含 `dept`） | `backend/.../DocumentController.java`（`getDocumentTree`） | 部分对齐 | 前端使用 `POST`，`DocumentController` 定义为 `GET`；参数 `dept` 以 body 形式发送而非 query | 统一调用方式（改为 GET+query 或后端支持 POST）；短期内确认接口是否兼容 `dept` 参数 |
| 文档列表 | GET | `/api/v1/documents` | `src/js/services/document-service.js`（`getDocumentList`，`POST` body 包含分页/筛选） | `.../DocumentController.java`（`getDocumentList`） | 部分对齐 | 同上：前端 POST，后端 GET；返回分页结构需确认 `DocumentListResponse` 字段名（`totalElements`, `content` 等）与前端分页控件一致 | 联调分页参数、校验 `status/keyword` 过滤是否生效 |
| 文档详情 | GET | `/api/v1/documents/{docId}` | `DocumentService.getDocumentDetail` | `DocumentController.getDocumentDetail` | 已对齐 | None | 确认权限错误码（`DOC_ACCESS_DENIED`）在界面定位 |
| 面包屑 | GET | `/api/v1/documents/{docId}/breadcrumb` | `DocumentService.getDocumentBreadcrumb` | `DocumentController.getBreadcrumb` | 已对齐 | None | |
| 文档更新 | PUT | `/api/v1/documents/{docId}` | `DocumentService.updateDocument` | `DocumentController.updateDocument` | 已对齐 | None | |
| 图片上传 | POST | `/api/v1/documents/{docId}/assets/images` | `DocumentService.uploadDocumentImage`（`FormData`） | `DocumentController.uploadDocumentImage` | 已对齐 | Front-end `FormData` 默认不提供 `Content-Type`，后端需允许 | 联调图片后端存储路径、`alt` 字段、返回 `DocumentImageUploadResponse`，确认 `docs/API_CONTRACT` 中未列出 `DocumentImageUploadResponse` 需要补充 |
| 图片访问 | GET | `/api/v1/documents/{docId}/assets/images/{filename}` | 前端直接构造 URL（`DocumentService.uploadDocumentImage` 上传后使用返回 URL） | `DocumentController.getDocumentImage` | 已对齐 | 需确认前端构造 `filename` 是否正确 URI 编码 | 创建联调用例验证 `Content-Type`/鉴权 |

## QA（问答检索）
| 接口 | 方法 | 路径 | 前端调用位置 | 后端控制器位置 | 当前状态 | 主要差异 | 建议动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 智能问答 | POST | `/api/v1/qa` | `src/js/services/qa-service.js`（`query`） | `backend/.../QAController.java`（`ask`） | 已对齐 | 前端内部将后端 `sources` 转为 `chunkId/docId/docTitle/text` 并默认填充 `metadata`，需确认 `QAResponse.SourceChunk` 字段满足需求 | 联调时核实返回字段是否包含 `content`, `docTitle`, `score`; 若缺少 `metadata` 需统一字段说明 |
| QA 历史 | GET | `/api/v1/qa/history` | `src/js/services/qa-service.js`（`getHistory`） | ——（未在 Controller 中定义） | 缺失 | 后端尚未实现 `history` 接口 | 立刻确认是否有历史查询需求，若是则由后端补充新接口，并同步文档 |
| SearchResult 结构 | —— | —— | `qa-service` 在结果中映射 `sources` | `backend/.../dto/qa/SearchResult.java` | 未对齐 | 前端期望 `sources`数组结构与 DTO 无 `metadata`，而后端只暴露 `chunkId/docTitle/content/score` | 统一字段命名与文档（如 `docs/API_CONTRACT.md`），明确是否需要 `dept/sensitivity`、`sectionTitle` |

## 管理端（仪表盘）
| 接口 | 方法 | 路径 | 前端调用位置 | 后端控制器位置 | 当前状态 | 主要差异 | 建议动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 系统指标 | GET | `/api/v1/admin/metrics` | `src/js/services/dashboard-service.js`（`getMetrics`） | `backend/.../admin/DashboardController.java`（`getMetrics`） | 已对齐 | `docs/API_CONTRACT.md` 提供了聚合响应结构，需确认后端实际字段完全匹配 | 联调时校对每个字段（`documents.byType`, `storage.usedSpace` 等） |
| 系统告警 | GET | `/api/v1/admin/alerts` | `DashboardService.getAlerts` | `DashboardController.getAlerts` | 已对齐 | None | |
| 健康检查 | GET | `/api/v1/admin/health` | 暂无前端直接调用（可由运维工具调用） | `DashboardController.healthCheck` | 部分对齐 | 前端未接入，但该接口由运维/监控使用，需同步文档 | 记录在 `docs/API_CONTRACT.md` 并在仪表盘文档中标注 |

## 跨模块风险项
| 关注点 | 说明 | 建议动作 |
| --- | --- | --- |
| 错误码与 401 处理 | `docs/API_CONTRACT.md` 列出大量业务码（`DOC_NOT_FOUND`/`QA_RATE_LIMITED` 等），但 `src/js/services/http-client.js` 只基于 HTTP 状态处理和统一 code/message；401 统一清除 session 但未细化校验 | 在联调时用真实后台返回进行测试，确保前端能根据 `code` 做差异提示，补充 `ApiResponse` 示例 |
| 分页结构 | `DocumentController.getDocumentList`、`ImportController` 等默认通过 query 参数分页，但前端使用 `POST` body（`DocumentService`）或 `request` 直接调用；`DocumentListResponse` 与 `RecentImportsResponse` 结构未在前端明确匹配 `totalElements/hasNext` | 与后端一起打印实际分页字段，确保前端分页组件解析正确；必要时在 `docs/API_CONTRACT.md` 中补充 metadata |
| QA 返回字段 | `QAResponse.SourceChunk` 结构与前端希望展示的 `sources` field 有偏差，且前端默认填充 `metadata.dept`/`sensitivity` | 明确最终字段契约（`docTitle/content/score/sectionTitle`），并将 `metadata` 字段写入文档或由后端补值 |
| 图片访问鉴权 | `DocumentController.getDocumentImage` 依赖 `@AuthenticationPrincipal`，前端构造 URL 时默认浏览器会携带 session cookie/Authorization header？ | 验证图片 URL 是否需要 Authorization header，若是需在前端图片组件中添加 `Authorization` 头或改用 signed URL |

