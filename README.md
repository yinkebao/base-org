# Base Org Frontend

基于 `Vite + Vanilla JS` 的多页面前端实现，覆盖：

- `A1` 登录页（本地登录 + SSO 入口）
- `A2` 注册页（实时校验 + 密码强度 + 自动登录）
- `B` 系统仪表盘（hash 子路由 + 指标/任务/告警）

项目总进度见：[项目进度.md](/Users/yinkebao/project/my/base-org/项目进度.md)

## 目录结构

```text
.
├── login.html
├── register.html
├── dashboard.html
├── src
│   ├── css
│   │   ├── base.css
│   │   ├── components.css
│   │   └── pages
│   ├── js
│   │   ├── config
│   │   ├── pages
│   │   ├── services
│   │   └── utils
│   └── assets
│       └── icons
└── tests
    ├── unit
    └── e2e
```

## 快速开始

```bash
npm install --cache /tmp/base-org-npm-cache
npm run dev
```

## 构建与测试

```bash
npm run lint
npm run test
npm run build
```

Playwright e2e（当前环境若系统权限受限，请按下方方式）：

```bash
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers npx playwright install chromium
PLAYWRIGHT_BROWSERS_PATH=/tmp/pw-browsers npm run test:e2e
```

## 环境变量

复制 `.env.example` 为 `.env.local`：

```bash
VITE_API_MODE=mock
VITE_API_BASE_URL=
```

- `VITE_API_MODE=mock`：走本地 mock 服务
- `VITE_API_MODE=real`：走真实后端 API
