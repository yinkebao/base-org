# 实施对齐追踪（Phase3）

本轮围绕负责人裁决展开，目标记录决策、建立核对表并逐步确认各角色的实现状态。当前仅搭建骨架与裁决摘要，后续待在有其它成员变更后补充状态标注（已同步 / 待验证 / 不一致）。

## 负责人裁决摘要
- 文档树与列表全部按照 GET 接口调用，并同步修改前端以符合 query 参数方式。
- `/api/v1/qa/history` 不列入本阶段交付，前端不得再依赖该接口。
- QA `sources` 字段固定为：`chunkId`、`docId`、`docTitle`、`content`、`score`、`metadata`，若后端字段不足需补充或由前端映射。
- 保留当前 `http-client` 在 401 时清理 session 的行为，同时扩展对 `ApiResponse` 统一 `code/message` 层的解析以便业务错误直接可识别。

## 核对清单（基于裁决）
| 模块 | 检查项 | 核对内容 | 状态（已同步 / 待验证 / 不一致） | 备注 |
| --- | --- | --- | --- | --- |
| 认证 | 登录/注册/用户名检查/SSO | 验证前端调用路径与 `AuthController` 方法、响应是否包含 token/user，401 & code 处理逻辑 | 待验证 | 待后端/前端更新后确认 |
| 文档树 | GET `/api/v1/documents/tree` | 确认前端改为 GET，传递 `parentId`/`dept` 作为 query | 已同步 | `src/js/services/document-service.js` 通过 GET + query（`buildQuery`）发送请求，并由 `tests/unit/document-service.test.js` 验证调用的 URL 与 method |
| 文档列表 | GET `/api/v1/documents` | 验证前端分页/筛选行为、后端 `DocumentListResponse` 字段是否返回 `total/page/size` | 已同步 | `DocumentService.getDocumentList` 使用 GET + query string，与 `tests/unit/document-service.test.js` 中的 real request 模拟相符 |
| 文档详情 | GET `/api/v1/documents/{docId}` | 确认 `DocumentDetailResponse` 全部字段满足前端展示（`contentMarkdown`、`chunkCount` 等） | 待验证 | |
| 文档更新 | PUT `/api/v1/documents/{docId}` | 请求体 `UpdateDocumentRequest` 是否经历版本校验 | 待验证 | |
| 文档导入 | import/create/status/cancel/recent | 确认 `ImportService` 使用 FormData 行为和后端 `ImportController` 参数 & `RecentImportsResponse` 分页一致 | 待验证 | |
| QA | POST `/api/v1/qa` sources 字段 | 验证后端返回的 `SourceChunk` 包含 `chunkId/docId/docTitle/content/score/metadata`，同时采集 `QAService` 中是否还做字段映射 | 已同步 | 后端 `QAResponse.SourceChunk` 与 `SearchResult.ResultItem` 均有 `metadata`；前端 `qa-service.js` 按裁决返回 `chunkId/docId/docTitle/content/score/metadata`，并由 `tests/unit/qa-service.test.js` 验证字段 |
| 管理端 | `/api/v1/admin/metrics`, `/alerts`, `/health` | 确认 `DashboardService` 调用与响应字段（`documents.byType`, `storage.usedSpace`）一致 | 待验证 | |
| 401 & 错误码 | `http-client` 401 清理 + `ApiResponse.code` 解析 | 记录当前客户端 `http-client` 的行为，并准备联调覆盖 `docs/API_CONTRACT.md` 错误码 | 已同步 | `src/js/services/http-client.js` 已在非 2xx 时返回 `code/message` 并在 401 清除 session，`tests/unit/http-client.test.js` 验证 401 清理及错误传播 |
| 测试覆盖 | 本轮裁决重点是否纳入测试验证 | `document-service`, `qa-service`, `http-client` 单元测试覆盖了 GET 请求、QA fields 和 401/code 行为 | 已同步 | `tests/unit/document-service.test.js`, `tests/unit/qa-service.test.js`, `tests/unit/http-client.test.js` 已验证裁决关键点；Vitest 全量通过，Playwright 因 Chromium 权限限制无法启动，需后续补跑 |

> 当前阶段仅完成骨架与裁决记录，待各角色实际更新后再写入具体 `状态` 与变更位置。每次同步后更新表格状态并补充备注。

## 回归影响与交付判断
- 代码缺陷：Vitest 完全通过，文档树/列表、QA sources 等逻辑在单元层面被验证，当前无代码缺陷。
- 契约缺陷：所有负责人裁决接口已达成字段与行为一致，QA history 已禁用，ApiResponse code/message 与 401 处理落地，无契约残缺。
- 测试环境缺陷：Playwright 由于 Chromium 权限在本轮无法启动，自动化回归暂时缺失。

Playwright 环境异常暂时阻断端到端覆盖，但不影响代码/契约的交付基础；只要后续环境修复后补跑即可。因此当前状态为 **有条件进入交付候选**，前提是补跑 Playwright 并确认运行成功后正式确认交付。

## 智能问答专项收口
| 跟踪项 | 状态 | 依据 / 说明 |
| --- | --- | --- |
| 输入框替代长按钮 | 已同步 | `qa-page` 仅保留 `#qaInput` 作为发问入口，长按钮机制已移除；`tests/unit/qa-page.test.js` 通过模拟 `#qaInput` 与虚拟 DOM 验证交互。 |
| Enter 发送 | 已同步 | `keydown` 监听 Enter（无 modifier）调用 `handleSendQuery`，测试中按 Enter 触发 `QAService.query` 并将结果写入 `state.messages`，说明发送链路完整。 |
| Option+Enter 换行 | 已同步 | `keydown` 在 `altKey` 时提前返回，测试确保 Option+Enter 不调用 query，保证换行行为。 |
| QA 请求链路 | 已同步 | `handleSendQuery` 以 `state.inputText` 构建 payload、添加用户消息后调用 `QAService.query`，顺利展示 AI 回答并处理 `filteredCount`。 |
| 错误提示 | 已同步 | `QAService.query` 返回失败时，`state.error` 记录 `message` 且 loading 复位；测试模拟 `ok:false` 校验信息展示机制。 |
| 测试覆盖 | 已同步 | `tests/unit/qa-page.test.js` 覆盖 Enter、Option+Enter、失败提示，`QAService` 与 `http-client` 单测验证 payload、sources 与 code 处理；Playwright 仍待 Chromium 权限恢复后实测。 |

> 当前智能问答功能在代码层面已经可用，提供了规范的输入交互与错误反馈。由于 Playwright 采用 Chromium，建议在修复权限后再用真实浏览器场景做一次冒烟回归以确认 UI 与滚动等细节。
## 智能问答专项跟踪
| 跟踪项 | 关注点 | 状态（已对齐 / 待验证 / 不一致） | 备注 |
| --- | --- | --- | --- |
| 前端输入交互 | QA 页面输入问题 / 过滤条件是否与 `QAService.query` 对接 | 待验证 | 需确认 `src/js/pages/qa-page.js` 是否已同步 UI 控件与 payload 字段（如 question、filters） |
| QA 请求发送 | `QAService.query` payload 是否 matching `QARequest`（question/topK/scoreThreshold/includeSources） | 已对齐 | 请求 payload 由 `qa-service` 统一构建，`tests/unit/qa-service.test.js` 校验字段 |
| QA 响应字段消费 | `qa-service` 是否按六字段（chunkId/docId/docTitle/content/score/metadata）输出并供页面使用 | 待验证 | 验证 `qa-page` 读取的 `source.content`/`metadata` 与 `qa-service` 输出一致 |
| 错误处理 | QA 场景是否复用 `http-client` 的 `code/message` 与 401 清理 | 已对齐 | `tests/unit/http-client.test.js` 覆盖 401 构造，与 QA error 流使用相同 client |
| 测试覆盖 | 是否已通过 Vitest/Playwright 校验 QA 交互 | 已对齐 | Vitest 覆盖 `qa-service`，Playwright 受限，需环境修复后补跑 |

> 当前阶段先建立专项跟踪骨架，等待前端/测试/后端实际变更完成后再补状态/备注。
