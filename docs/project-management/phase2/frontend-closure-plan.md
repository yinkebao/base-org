# 前端收口实施计划

## 页面/模块收口清单

### 登录与注册
- **目标**：确保 `login.html` 与 `register.html` 的表单验证、错误提示、语言切换、SSO 与用户名可用性校验具备可重复联调状态，并能将认证结果存储至 `session-service.js` 以供后续页面使用。
- **涉及文件**：`src/js/main-login.js`、`src/js/main-register.js`、`src/js/pages/login-page.js`、`src/js/pages/register-page.js`、`src/js/services/auth-service.js`、`src/js/services/session-service.js`。
- **依赖接口**：`/api/v1/auth/login`、`/api/v1/auth/register`、`/api/v1/auth/sso/start`，由 `src/js/config/api-endpoints.js` 定义，期望返回 `{ data: { token, user } }`，并在失败时携带 `code` 供前端映射。
- **风险**：若 token 格式或错误码不符，`http-client.js` 无法注入 `Authorization`，会导致整个仪表盘无法加载；SSO 需要后端提供 redirect URL，否则按钮会报错。
- **验收标准**：成功登录/注册后 `saveSession` 存储信息，进入 `/dashboard.html`；错误场景展示 `login-page`/`register-page` 中 `setText` 设定的错误文案；`AuthService.checkUsername` 与 `debounce` 校验在网络延迟下稳定响应。
- **联调状态**：需等待后端确认 `/api/v1/auth/*` 合约（包括 SSO）后才能完全收口，当前可先行调试本地 mock（`src/js/services/mock`）以验证交互逻辑。

### 主仪表盘与多路由容器
- **目标**：将 `dashboard.html` 作为多路由容器，可靠管理 `overview/docs/doc-view/imports/qa/alerts/audit/settings` 的 hash 路由，初始化侧边栏折叠、指标/导入/告警/审计/QA/文档模块。
- **涉及文件**：`src/js/main-dashboard.js`、`src/js/pages/dashboard-page.js`、`src/js/services/dashboard-service.js`、`src/js/utils/import-validators.js`。
- **依赖接口**：`/api/v1/admin/metrics`、`/api/v1/admin/alerts`、`/api/v1/admin/health`、`/api/v1/documents/import/recent` 等，所有路径集中在 `api-endpoints.js`。
- **风险**：单个模块初始化失败可能阻塞整个页签渲染；`initDashboardPage` 需要容错机制（已在 `catch` 中替换 DOM 节点）以免空白页。
- **验收标准**：哈希切换加载对应模块、`renderMetricCards` 数据来自 `dashboard-service.js` 的稳定接口、侧边栏折叠状态在 `localStorage` 中持久化、`overview`/`alerts`/`audit` 面板可显示至少 mock 数据。
- **联调状态**：指标与告警依赖后端填充字段（token 今日消耗、向量 DB 状态等），需后端明确字段后再固化 UI；在等待期间可使用 `dashboard-service.js` mock 版本验证渲染。

### 文档中心（列表/树/详情/编辑）
- **目标**：前端呈现文档树、列表、详情、面包屑与编辑器状态（包括 `createToastDocEditor`、`DOC_AUTOSAVE_INTERVAL`），并完成文档创建、更新、图片上传入口。
- **涉及文件**：`src/js/pages/document-center.js`、`src/js/services/document-service.js`、`src/js/utils/markdown-headings.js`、`src/js/utils/toast-doc-editor.js`。
- **依赖接口**：`/api/v1/documents/tree`（POST）、`/api/v1/documents`（列表/创建）、`/api/v1/documents/{docId}`（GET/PUT/图片上传）、`/api/v1/documents/{docId}/breadcrumb`，需确认访问控制、metadata 字段。
- **风险**：文档树渲染依赖完整节点数据（`renderTree` 访问 childrenId），若接口仅提供部分字段会渲染失败；编辑器初次加载（`markdownRendererPromise`）需防止重复请求；保存失败时缺少明确弹窗反馈。
- **验收标准**：文档树/面包屑按 `DocumentService` 内容对齐、点击树节点加载详情、编辑器具备自动保存提示、创建/更新接口可通过 `DocumentService.createDocument/updateDocument` 调用。
- **联调状态**：文档 API 需联调确认分页/权限/元数据字段后才能闭环；在接口稳定前可先行做本地 mock 以检查渲染与 DOM 事件。

### 导入流程
- **目标**：完成导入页面四步 `IMPORT_STEPS`（来源、元数据、分块、预览）的 UI 呈现、文件上传、字段验证、任务提交与状态轮询。
- **涉及文件**：`src/js/pages/dashboard-page.js`（导入状态、renderImportsTable）、`src/js/services/import-service.js`、`src/js/utils/import-validators.js`。
- **依赖接口**：`ImportService.createImportTask` 对应 `/api/v1/documents/import`，任务查询 `/api/v1/documents/import/{id}`、取消 `/cancel` 需与后端确认返回 `progress`、`status`；`SUPPORTED_UPLOAD_EXTENSIONS` 影响文件选择。
- **风险**：若后台不提供 `progress` 或 `status` 字段，数组渲染与进度展示（`renderImportsTable`）无法展示有效信息；文件上传/URL 方案未定时无法提供效果反馈。
- **验收标准**：每步表单校验规则通过 `validateImportMetadata`/`validateImportSource`，成功调用 `createImportTask` 后列表新增任务并显示 `progress`，取消/失败状态可在 `renderImportsTable` 中响应。
- **联调状态**：创建任务和任务状态依赖后端接口字段与下载状态；在接口冻结前需与后端确认 `chunkConfig`、`metadata` 的数据结构，其他环节可先完成 UI 布局与表单校验。

### QA 工作区
- **目标**：确保 QA 页可发送问题、展示消息、引用来源、权限过滤结果与筛选提示，历史记录接口可选。
- **涉及文件**：`src/js/pages/qa-page.js`、`src/js/services/qa-service.js`、`src/js/utils/dom.js`（用于 DOM 操作）。
- **依赖接口**：`/api/v1/qa`（`QAService.query`）、`/api/v1/qa/history`（`QAService.getHistory`）、可选的 `queryStream` 用于流式演示。
- **风险**：若后端未返回 `sources`，`renderSourcePanel` 会展示空内容；`qa-page` 依赖 `filteredCount` 来提示权限过滤，需与后端同步过滤结果。
- **验收标准**：输入问题并成功收到回答（`messages` 更新）、来源面板按 `sources` 渲染、`QAService.getHistory` 可选触发并展示历史。
- **联调状态**：需要等待 `/api/v1/qa` 返回 `promptHash`、`sources` 等字段后才能封闭；可提前搭建 UI 和 mock 逻辑。

### 管理端指标/告警/健康检查页
- **目标**：在仪表盘中展示指标卡片（token、向量 DB、LLM、导入任务）、告警列表与审计记录，支持告警等级视觉区分与 audit 列表滚动。
- **涉及文件**：`src/js/pages/dashboard-page.js`、`src/js/services/dashboard-service.js`。
- **依赖接口**：`/api/v1/admin/metrics`、`/api/v1/admin/alerts`、`/api/v1/admin/health`，需确认告警 `level` 字段、审计时间排序。
- **风险**：数据更新频繁时页面缺乏实时刷新机制；若 `alerts` 接口返回字段变化（如 `level` 改名）会破坏颜色/分类。
- **验收标准**：指标卡 `renderMetricCards` 显示 `metrics` 提供的字段，告警卡片与审计列表按等级/时间排序、`levelClass` 与 `statusClass` 适配。
- **联调状态**：需后端把对应字段固定下来后才能完成颜色映射与告警细化；可先行做 UI 结构与 mock 数据填充。

### 多入口构建与状态管理
- **目标**：验证 `vite.config.js` 多入口（`index/login/register/dashboard`）与 `package.json` 脚本、`session-service.js` 的状态存储逻辑，确保 `build` 输出脚本可被对应 HTML 引入。
- **涉及文件**：`package.json`、`vite.config.js`、`src/js/services/session-service.js`、`src/js/config/api-endpoints.js`、`src/js/services/http-client.js`。
- **依赖接口**：`API_BASE_URL` 由 `src/js/config/env.js` 提供，`http-client.js` 通过 `request` 提供统一 error handling 与 `Authorization` header。
- **风险**：构建打包后静态资源路径（`dist`）如果缺少某个入口会导致页面 404；`session-service` 依赖 `localStorage`，在 SSR/测试环境可能无法获取。
- **验收标准**：`npm run build` 后 `dist` 目录含 login/register/dashboard 页面静态资源，可在 `dashboard.html` 中加载；`session-service` 在短暂无 session 时不会报错（已做 null check）。
- **联调状态**：构建与状态管理可先行执行（只要 `API_BASE_URL` mock），不依赖具体后端；但需要等接口地址定稿后统一注入 env。

## 推荐执行顺序
1. **多入口构建与状态管理**（可先行）——确保静态资源与 `session-service` 正常，便于后续页面验证。  
2. **登录/注册与主仪表盘基线**（需后端冻结 `/api/v1/auth/*`）——一旦认证能稳定，仪表盘页面才有前提可以加载。  
3. **文档中心与导入**（依赖文档与导入接口冻结）——在认证通的前提下完成文档树、编辑、导入流程。  
4. **QA 与管理端视图**（依赖 `/api/v1/qa`、`/api/v1/admin/*`）——在以上接口确认后收口问答、告警/指标。  
5. **验收与联调回归**——利用 `vitest`、`playwright` 脚本跑关键页面 smoke test，确保 `http-client`、`session-service` 的状态管理在整体流程中稳固。  

## 依赖联调与可先行项
- **需要等待后端冻结**：认证接口、文档导入、QA、告警/指标；这些接口字段直接决定前端 API payload 与渲染内容，建议在接口稳定后同步一个版本。  
- **可先行的前端工作**：构建配置与状态管理验证、页面 UI 结构、表单校验逻辑、mock 数据填充；只要 `API_BASE_URL` 模拟值和 mock API 一致，就能保障页面本地可视化，便于后续联调。  
