# 第二阶段主链路验收矩阵与测试执行基线

## 主链路验收矩阵
| 验收项 | 前置条件 | 测试数据 | 预期结果 | 优先级 | 依赖角色 | 接口冻结依赖 |
| --- | --- | --- | --- | --- | --- | --- |
| 登录与注册成功跳转 | 后端 auth 接口可用；本地 mock server（`playwright.config.js`）已启动 | 正常管理账号（`admin@system.com`）与新注册账号（`tests/e2e/auth-flow.spec.js` 中模板） | 登录后跳转 `dashboard.html#overview`；注册后自动登录并展示用户信息 | 最高 | 后端（API 保持 auth contract）、前端（按钮 state） | `POST /api/v1/auth/login`, `POST /api/v1/auth/register` 固定响应格式 |
| 认证失败及401会话清理 | auth 接口能返回 401；`tests/integration/auth-api.integration.test.js` 中设置 `VITE_API_MODE=real` | 登录时传入错误密码/不存在用户 | 返回对应错误码（如 `AUTH_INVALID_CREDENTIALS`）；前端清除会话并重定向登录 | 最高 | 后端、前端、安全（session） | 同上，同时需要 `GET /api/v1/auth/check-username` |
| 文档导入流程 | 导入页面可访问（`dashboard.html#imports`）；`tests/e2e/imports-flow.spec.js` 中配置 chunk 参数 | 指定 URL 或上传文件、部门、版本、chunk/overlap | 每步显示提示，最终提交后出现“任务状态”区域；请求参数符合 `validateImportSource`/`validateChunkConfig` | 高 | 后端（import API）、前端（向导 UI） | `POST /api/v1/documents/import` 字段 |
| 文档树/列表/详情/编辑链路 | 文档数据在 `document-service` 模拟/后端存量；`tests/e2e/docs-flow.spec.js`, `edit-flows` 可访问 | 选择 `产品设计部` 过滤、打开 `doc-arch-v24` 等 | 部门筛选生效、文档详情能展示、编辑可保存、富文本功能存在、自动保存提示 `已保存` | 高 | 前端、后端、联调（document service） | `GET /api/v1/documents`, `PUT /api/v1/documents/{id}` |
| QA 生成与检索（待完成） | QA 页面或接口可用；问答 API 需冻结字段 | 指定检索语、上下文 metadata（role/ACL） | 返回有 traceId、结果含 Metadata；敏感字段剔除；向量结果包含 docId | 高 | 后端（QA API）、前端（QA 页面）、测试 | `POST /api/v1/qa` 接口规范 |
| 审计日志与 promptHash 记录 | 审计服务可写入；日志存储可调用 | 触发任意 LLM 请求或敏感操作 | audit log 包含 user/action/resource/timestamp/promptHash；错误状态应被记录 | 中 | 安全/后端、审计 | `POST /api/v1/audit` 或后端链路 |
| 错误处理与安全（密码强度、锁定） | 校验器逻辑存在（`tests/unit/validators.test.js`, `password-strength.test.js`） | 弱密码、重复用户名/邮箱、锁定账号 | 返回友好错误码/消息，前端显示，账号锁定时禁用操作 | 高 | 后端（验证器、安全），前端 | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| 权限与 ACL 过滤 | metadata filter 在向量查询后执行；联调清单确认 CORS/OPTIONS | 带 role cookie 或不同 dept | 低权限用户看不到高敏感文档，vector query 中包含 metadata filter | 中 | 后端、联调（接口契约） | 向量检索 API 与 `/api/v1/documents` |
| 安全/监控报警 | Prometheus / Grafana target 可访问；监控 endpoint（或 mock） | 访问监控接口、注入错误 | 返回指标（`up`, `http_requests_total`）且 alert 规则有响应；测试记录带 traceId | 中 | 运维、安全、后端 | 监控端点 `/metrics` |
| 性能目标（导入、QA） | 性能脚本可执行；环境支持 50+ 并发 | 批量导入模拟请求、QA 并发查询 | P95 latency ≤ 3s（不含外部 LLM） | 中 | 性能/后端 | 各接口稳定，需固定导入/QA contract |

## 每条验收项的详细说明
1. **认证登录/注册成功跳转**：前置条件是 `playwright.config.js` 已启动 mock server，测试数据可用 `admin@system.com` 和 `user_<timestamp>`；预期结果包括 URL 跳转与界面显示用户名、`session` token 存储；依赖后端保持 `POST /api/v1/auth/login`/`register` 接口契约；优先级最高，可先行准备 UI 自动化脚本。
2. **认证失败与 401 清理**：依赖接口能返回 401；测试可以仿造 `tests/integration/auth-api.integration.test.js` 的顺序，确保 session 清除；后端与前端需共同完成验证；接口冻结后执行自动化回归。
3. **文档导入流程完整性**：依赖导入 API，测试数据包括 `imports-flow` 中的 URL/选择`; 预期挂载在导入结果界面；优先级高，需后端合并导入 API 字段冻结再执行。
4. **文档树/详情/编辑**：前置条件是文档中心正常加载；测试数据使用 `doc-arch-v24` 等固定 docId；预期包括筛选、进入详情、编辑并保存后闪现“已保存”；依赖 `GET /api/v1/documents`/`PUT`；可以在接口冻结前准备 Playwright 逻辑。
5. **QA 生成与结果验证**：前置条件 QA 页面可访问或 QA API 接口稳定；测试数据需包含 role metadata；预期返回 traceId 与可读回答；优先级高，需接口冻结后才可做完整验证。
6. **审计 promptHash 记录**：依赖 audit 后端；前置条件为审计记录写入机制开启；测试数据可通过 QA 请求或文档操作；预期 log 含 `promptHash`/`user/action/resource`；优先级中，接口冻结后再验证。
7. **错误处理与安全**：依赖密码/字段校验规则；测试数据为弱密码、已占用邮箱；预期错误码一致；优先级高，可与认证同步执行。
8. **权限/ACL 过滤**：前置条件为不同敏感等级文档与角色；测试数据给低权限用户；预期未显示敏感文档；优先级中。
9. **安全/监控指标**：依赖监控 endpoint 可访问；测试数据通过 `curl /metrics`；预期指标返回与 alert 触发；优先级中，监控配置冻结后验证。
10. **性能目标**：需接口和环境支持 50+ 并发；测试数据为批量导入任务与 QA 请求；预期 50 并发下 P95 ≤ 3s；中等优先级。

## 高风险专项测试建议
- QA 与向量检索链路的 smoke 测试：缺乏 QA 副本，需要设计自动化脚本同时验证向量结果、promptHash、metadata filter，建议借助 `tests/e2e/docs-flow.spec.js` 的登录逻辑复用。
- 审计/安全合规：重点验证 `AuditLog` 记录（时间戳、action、resource）、敏感字段过滤与 promptHash 计算，可在后端日志系统（`backend` 代码）部署测试 hook。
- 性能与监控：引入负载脚本（如 k6 或自定义 Node 脚本）模拟导入任务与 QA 请求，结合 Prometheus `/metrics` 采集抓取数据，确保各项指标在 50 并发下稳定。
- 接口契约回归：为 `documents`, `qa`, `audit`, `admin` 等 API 编写契约测试，采用 `tests/integration` 模板验证响应字段与错误码。

## 回归准入建议
- 凡涉及认证、导入、文档管理、QA、审计、安全、性能的变更必须通过 `npm run test`（运行 `tests/unit`）与 `npm run test:e2e`；优先运行 `tests/integration/auth-api.integration.test.js` 的 real 模式并确认联调清单中 CORS / 401 / traceId 条目。
- 在接口冻结前可独立准备 Playwright/接口脚本，但正式执行需等 API contract 冻结（如 `POST /api/v1/qa`、`/documents/{id}`）；回归周期建议每次发布或关键功能变更前至少执行一次完整 Playwright 套件与接口联调脚本。
- 回归失败时，利用 `test-results/` 生成的 `error-context.md` 分析失败点，并结合审计日志/监控告警追溯，记录于 `docs/project-management/phase2` 以便下一阶段审计。
