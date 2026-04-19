# 前端现状与后续实施分解

## 当前已完成页面/能力
- 登录、注册、主仪表盘分别由 `src/js/main-login.js`、`src/js/main-register.js`、`src/js/main-dashboard.js` 挂载，页面加载即调用 `src/js/pages/login-page.js`、`src/js/pages/register-page.js`、`src/js/pages/dashboard-page.js` 中的初始化逻辑，初步完成表单校验、语言切换、SSO 跳转按钮与全局错误提示等交互元素。
- `src/js/pages/login-page.js` 实现了输入验证、错误映射、按钮 loading 控制与 `AuthService.login`/`AuthService.startSso` 的调用；`src/js/pages/register-page.js` 包含用户名可用性校验、密码强度可视化、服务条款校验与 `AuthService.register` 请求，这两份文件覆盖了登录注册所需的前端行为。
- 仪表盘的 `src/js/pages/dashboard-page.js` 目前支持 `overview/docs/doc-view/imports/qa/alerts/audit/settings` 多路由，渲染指标卡片、导入任务表格、告警卡片、审计列表与侧边栏折叠状态（persisted via `localStorage`），并在 `createDefaultImportState` 中定义导入流程状态与校验函数。
- 文档中心在 `src/js/pages/document-center.js` 负责文档树渲染（`renderTree`）、面包屑（`renderBreadcrumb`）、文档列表/详情展示、元数据路径构建、文档节点展开状态持久化与编辑器自动保存间隔（`DOC_AUTOSAVE_INTERVAL`），同时暴露 `createDefaultDocumentState` 等一系列状态构建函数，提供页面级状态管理。
- QA 工作区的 `src/js/pages/qa-page.js` 基于 `createDefaultQAState` 管理消息、来源、过滤计数和加载状态，提供 `renderMessages`、`renderSourcePanel`、`renderLoadingState` 等函数并借助 `QAService.query` 取得问答结果，实现前端聊天式渲染。
- 所有 API 调用通过 `src/js/services` 目录统一封装：`auth-service.js`/`session-service.js` 管理登录、会话状态；`document-service.js` 支持文档树、列表、详情、面包屑、创建、更新与图片上传；`import-service.js` 将导入、任务查询、取消封装；`qa-service.js` 负责 QA 查询与历史；`dashboard-service.js`、`http-client.js` 等提供统一请求封装，且基础地址与接口路径集中在 `src/js/config/api-endpoints.js` 与 `src/js/config/env.js` 配置。
- 状态管理方面，`session-service.js` 通过 `localStorage` 持久化会话并提供 `isAuthenticated` 判断；`dashboard-page.js`、`document-center.js`、`qa-page.js` 等模块分别定义 `createDefault...State` 工厂函数，配合路由/hash 查询解析（`getHashQuery`/`getRouteDocId`）实现页面状态隔离。
- 构建体系由 `package.json` 定义的 `vite` 相关脚本与 `vite.config.js` 的 Rollup 多入口（index/login/register/dashboard）支撑，确保构建输出包含所有页面入口，且 `optimizeDeps.include` 明确预打包 `@toast-ui/editor`、`marked` 等核心依赖以辅佐编辑与渲染能力。

## 当前未完成页面/能力
- 虽然 `document-center.js` 已提供文档树、列表、编辑器状态与面包屑逻辑，但文档内容的富文本/markdown 编辑器实际 UI 组件挂载、保存流程的 UX 反馈（比如 `createToastDocEditor` 的集成、中断恢复）尚需确认，现有代码以渲染函数为主，缺少与后端接口的直接联系（仅有 `DocumentService.updateDocument` 等但未在 file 中明确调用点）。
- 导入流程在 `src/js/pages/dashboard-page.js` 中定义了 `IMPORT_STEPS`、`createDefaultImportState` 及 `renderImportsTable`，但每步表单、文件上传、分块配置渲染与 `ImportService.createImportTask` 隐含的 API payload 映射之间的校验与用户反馈未被完全覆盖，缺乏可视化进度条、失败恢复界面与长期任务轮询机制的 UI 表现。
- QA 页面以 `QAService.query` 取数据、`renderMessages` 呈现对话，当前并未说明如何同 `dashboard` 的 `createDefaultQAState` 共享上下文（如 `filteredCount`、`selectedSource`）或储存历史；另未看到与 `QAService.getHistory` 的 UI 入口，说明历史记录及权限过滤未完成。
- 管理端的 `alerts`/`audit`/`settings` 视图在 `dashboard-page.js` 中只见到简化的渲染片段与卡片（`renderAlerts`、`renderAudits`），尚不清楚后端指标、告警、健康检查数据若在较高频变动时的页面刷新策略、图表/表格排序、阈值提示等细节。
- 构建与状态方面虽然有 `package.json`、`vite.config.js`，但尚未针对多页面静态资源路径、环境切换（`API_BASE_URL`）、以及组件/状态的按需加载机制做进一步说明或测试；尤其 `vitest`/`playwright` 脚本也未与现有页面结构明确匹配。

## 高优先级风险
1. 登录/注册及会话完全依赖 `src/js/services/http-client.js` 的 `Authorization` 注入与 `API_BASE_URL`，如后端域名或 token 格式变更（`request` 中 `headers` 构造）会在最前端直接导致所有 API 请求失败且无回退。
2. 文档、导入与 QA 页面共享 `src/js/pages/dashboard-page.js` 中的路由与状态切换逻辑，若 hash 路由跳转或 `document-center` 状态初始化抛出异常会影响整个仪表盘内容加载，风险集中在 `initDashboardPage` 尝试渲染多个模块时未做重试。
3. 文档编辑/保存、导入任务提交/取消与 QA 查询均依赖后端相关 API（`DocumentService.updateDocument`、`ImportService.createImportTask`、`QAService.query` 等），一旦 REST contract 改动或访问权限配置不一致，前端当前缺少降级提示或 mock 数据检测导致页面空白。

## 依赖联调/后端确认的问题
- 登录注册：需确认 `/api/v1/auth/login`、`/api/v1/auth/register` 以及 SSO `/api/v1/auth/sso/start` 的返回格式与 `login-page.js`/`register-page.js` 中的错误码映射一致，特别是 `AuthService.login` 期待 `{ data: { token, user } }`。
- 文档中心：`document-center.js` 与 `DocumentService` 依赖 `/api/v1/documents/tree`（POST）、`/api/v1/documents`（列表/创建）、`/api/v1/documents/{docId}`（详情、更新、图片上传）等接口，需确认分页、权限、元数据字段名、docId 类型与前端渲染逻辑匹配。
- 导入：校验 `dashboard-page.js` 中 `IMPORT_STEPS` 所展示字段与 `ImportService.createImportTask` 真实 payload（文件/URL/metadata/chunkConfig）的一致性，且需要后端补充导入任务状态接口（`/documents/import/{id}` GET）与取消 API `/cancel` 的语义。
- QA 与审计：前端期望 `/api/v1/qa` 返回 `sources`、`result.data.answer` 及 `promptHash`，还需要 `/api/v1/admin/metrics`、`/api/v1/admin/alerts` 以及 `/api/v1/admin/health` 提供稳定数据协会 `dashboard-page.js` 的指标卡上显示的 `metrics`、`alerts`、`health` 字段。
- 构建/状态：`package.json` 中 `vite build` 以及 `vite.config.js` 指定四个入口，需确认构建部署流程（cdn 静态资源路径、`API_BASE_URL` 注入、`node -e 'rmSync'` 重置机制）与后续环境变量一致，避免多页面路径缺失或入口混乱。

## 第二阶段建议任务拆解（按优先级）
1. **入口与构建一致性验证**：串联 `vite.config.js` 的 `input`（index/login/register/dashboard）、实际 HTML 模板（如 `login.html`、`dashboard.html`）与 `src/js/main-*.js` 之间的挂钩，确认 `dev`/`build` 输出静态资源命名不会导致页面找不到对应脚本，再结合 `package.json` 脚本确保 `npm run build` 后 `dist` 包含所有页面。
2. **完善文档中心与导入编辑体验**：基于 `document-center.js` 的 `renderTree`、`buildFolderPath`、`createDefaultDocumentState`，补齐富文本编辑器集成（`createToastDocEditor`）、文档保存/草稿策略，与 `DocumentService.updateDocument`、`createDocument` 的 payload 对齐，同时补充导入每步的 UI 表单控件、文件上传组件与 `ImportService.createImportTask` 参数映射，并增加失败提示/进度轮询。
3. **QA 与管理端数据同步**：确保 `qa-page.js` 中 `createDefaultQAState` 的 `messages`、`filteredCount`、`selectedSource` 能通过 `QAService.query`/`queryStream` 获取的数据维持全链；补充 QA 历史入口连接 `QAService.getHistory`，并与 `dashboard-page.js` 中的 `createDefaultQAState`（line ~29）统一状态引用。管理端的 `alerts`/`audit`/`metrics` 需要稳定数据源（`dashboard-service.js` + `dashboard-page.js`）。
4. **构建/集成测试与状态稳定**：针对 `http-client.js`、`session-service.js` 以及 `API_ENDPOINTS` 定义的路径，制定 mock/回退策略（现有 `src/js/services/mock` 可扩展），确保在后端尚未准备好时也能进行前端回归验证，顺便构建 `vitest`/`playwright` 对关键页面的 smoke tests。
5. **联调与后端确认会议**：结合 `api-endpoints.js` 中列出的 `/documents/import`、`/admin/metrics`、`/auth/check-username` 等必需接口，与后端确认字段、权限、错误码，明确谁负责更新 `dashboard-page.js` 中 `renderMetricCards`、`renderAlerts` 等展示逻辑对应的数据结构。
