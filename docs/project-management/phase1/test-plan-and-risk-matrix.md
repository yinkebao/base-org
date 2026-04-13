# 第一期测试计划与风险矩阵

## 当前已有测试资产
- Playwright 端到端脚本位于 `tests/e2e`，其中 `auth-flow.spec.js` 覆盖登录与注册流程，`imports-flow.spec.js` 校验导入向导，`docs-flow.spec.js` 与 `edit-flows.spec.js` 负责文档中心的浏览、编辑、保存与目录操作，`playwright.config.js` 定义了 Chromium 项目与本地 mock 服务器配置。
- Vitest 单元覆盖在 `tests/unit/*.test.js`，如 `document-center.test.js`、`document-service.test.js`、`import-validators.test.js`、`validators.test.js` 等，确保文档树、创建/更新、导入参数校验与密码强度逻辑的正确性；`vitest.config.js` 仅包含 `tests/unit`。
- 接口/联调测试集中在 `tests/integration/auth-api.integration.test.js`，校验注册、登录、用户名/邮箱重复、弱密码、401 处理以及 Authorization header 管理；同时在文件底部列有联调清单，指明 CORS、401、traceId 等门禁需求。
- CI/执行线索由 `package.json` 中的 `npm run test`（运行 Vitest）、`npm run test:e2e`（运行 Playwright）与 `test-results/` 中已运行的 `auth-flow`、`docs-flow`、`edit-flows` 截图，体现当前自动化执行链路。

## 核心业务验收范围
1. **认证（登录/注册）**：`tests/e2e/auth-flow.spec.js` 提供 UI 成功跳转与注册自动登录验证，`tests/integration/auth-api.integration.test.js` 补充接口层的注册、登录、401 清理、Authorization header、错误码格式等。
2. **文档导入**：`tests/e2e/imports-flow.spec.js` 覆盖导入向导的多步骤交互，`tests/unit/import-validators.test.js` 验证上传来源、chunk/metadata 校验逻辑。
3. **文档管理与编辑**：`tests/e2e/docs-flow.spec.js` 和 `edit-flows.spec.js` 验证列表筛选、详情导航、目录新建、编辑、自动保存、富文本工具、图片表格插入等关键场景，`tests/unit/document-service.test.js` 确保文档树/详情/编辑操作的一致性。
4. **服务层验证**：`tests/unit/mock-auth.test.js`、`validators.test.js` 对模拟身份与字段校验提供基础保障。
5. **自动化执行线索**：`package.json` 的 `test`/`test:e2e` 与 `playwright.config.js` 中的 `webServer` 设置，形成本地回归可执行路径。

## 高风险未覆盖区域
- **问答检索（QA）**：仓库中未找到与 `qa` 接口或页面的 API/端到端测试，现有脚本仅涵盖文档展示与编辑，未验证相关向量检索或 LLM 调用。
- **审计与安全策略**：缺乏对 `AuditLog`、`promptHash`、敏感字段剔除、安全分级 `public|internal|confidential|secret` 的自动化验证；也未见审计 API 的接口/契约测试。
- **监控与性能**：无专门负载/性能脚本、Prometheus + Grafana 指标捕获、告警验证或回放，CI 仅执行功能性测试，P95 latency、向量查询吞吐等未知。
- **接口契约**：除了认证相关接口，其它核心端点（如 `POST /api/v1/documents/import`、`/api/v1/documents/{id}`, `/api/v1/qa`, `/api/v1/admin/*`）缺乏 API 自动化或契约验证，前后端对齐风险高。
- **回归/安全回归**：当前 `tests/e2e` 主要面向 happy path，未覆盖登录失败、注册限流、导入失败、LLM 超时等高风险复现。

## 第二阶段测试任务拆解（按优先级）
1. **Authentication & Authorization + Audit 合规**（优先级：高）：在 Playwright 或接口测试中扩展登录失败、锁定账户、401 自动清理、traceId/Authorization header 传播，以及 `tests/integration/auth-api.integration.test.js` 底部联调清单中提到的 CORS、OPTIONS、错误码一致性；新增对审计日志、promptHash 报文的模拟记录。
2. **QA/文档检索链路**（优先级：高）：补全指向 `qa` 接口的自动化用例，验证搜索结果、向量 ID、角色/ACL 过滤；若需可复用 `tests/e2e/docs-flow.spec.js` 的登录态并扩展到 `/dashboard.html#qa` 页面或直接调用 `qa` API。
3. **接口契约+导入边界**（优先级：中）：为文档导入、管理、审核类接口编写契约测试，验证 `POST /api/v1/documents/{id}` 的字段、状态码、敏感信息掩码，覆盖 `import-validators` 已知边界。
4. **性能/监控与安全回归**（优先级：中）：引入轻量的性能脚本（如 k6、Playwright load run）检查导入任务、QA 响应在 50+ 并发下的 P95，结合 `backend` 的监控配置（如 Prometheus 端点）确保 alert 可读；在安全面增加 credential fuzz、密码强度 thresholds 的回归覆盖。
5. **回归覆盖与自动化维护**（优先级：低）：将 `tests/results` 中的 Playwright 执行结果纳入回归指标，定期复跑 `tests/e2e` 与 `tests/unit`；若现实需要，考虑在 `package.json` 中引入 `lint` + `test` 组合脚本用于 CI 质量门控。

## 准入/回归建议
- **准入门槛**：当认证、导入、文档管理、QA、审计或安全相关需求准备就绪后，必须完成对应的 Playwright/接口测试并通过 `npm run test` 与 `npm run test:e2e`；在 GitHub/CI 中可新增 `playwright` & `vitest` 并行任务，成功率作为准入阈值。
- **回归策略**：每次发布前重新执行 `tests/unit` 与 `tests/e2e`，同时补充 `tests/integration/auth-api.integration.test.js` 的 real 模式（`VITE_API_MODE=real`）运行，确保真实后端契约；性能与监控回归可定期在独立环境执行，若结果显著下降需在准入列表中标记 fail。
- **持续观察**：利用 `test-results/` 的 Playwright 错误上下文（如 `error-context.md`）结合监控告警（Prometheus + Grafana）追踪失败根因，并将关键测试路径记录到 `docs/project-management` 以便后续审计。
