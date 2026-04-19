# 前后端联调测试用例

## 测试环境配置
- 前端: http://localhost:5173 (Vite dev server)
- 后端: http://localhost:8080 (Spring Boot)
- 模式: VITE_API_MODE=real

---

## 1. 认证模块测试用例

### TC-001: 用户名检查 - 可用用户名
**前置条件**: 无
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/auth/check-username?username=newuser123`
2. 验证响应状态码为 200
3. 验证响应中 `available` 为 `true`

**预期结果**:
```json
{
  "ok": true,
  "data": {
    "username": "newuser123",
    "available": true,
    "message": "用户名可用"
  }
}
```

**状态**: ✅ PASS (已验证)

---

### TC-002: 登录 - 凭据错误
**前置条件**: 无
**测试步骤**:
1. 发送 POST 请求到 `/api/v1/auth/login`
2. 请求体: `{"username":"wrong","password":"wrong"}`
3. 验证响应状态码为 200
4. 验证响应中 `ok` 为 `false`
5. 验证 `code` 为 `AUTH_INVALID_CREDENTIALS`

**预期结果**:
```json
{
  "ok": false,
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "用户名或密码错误"
}
```

**状态**: ✅ PASS (已验证)

---

### TC-003: 注册 - 新用户注册
**前置条件**: 用户名可用
**测试步骤**:
1. 发送 POST 请求到 `/api/v1/auth/register`
2. 请求体: `{"username":"testuser","password":"Test@123","email":"test@example.com"}`
3. 验证响应状态码为 200
4. 验证返回 `user` 和 `token`
5. 使用返回的 token 请求需要认证的接口

**预期结果**: 注册成功，返回 token

**状态**: ❌ FAIL - 后端返回 INTERNAL_ERROR

---

### TC-004: 登录 - 成功登录
**前置条件**: 数据库中存在测试用户
**测试步骤**:
1. 发送 POST 请求到 `/api/v1/auth/login`
2. 请求体: `{"username":"testuser","password":"Test@123"}`
3. 验证响应状态码为 200
4. 验证返回 `user` 和 `token`
5. 验证 token 被存储到 localStorage

**预期结果**:
```json
{
  "ok": true,
  "data": {
    "user": { "id": "...", "username": "testuser", ... },
    "token": "jwt-token"
  }
}
```

**状态**: ⏸️ BLOCKED - 等待注册接口修复

---

### TC-005: Token 认证 - Bearer Token
**前置条件**: 已登录，拥有有效 token
**测试步骤**:
1. 使用 token 发送 GET 请求到 `/api/v1/documents`
2. 请求头: `Authorization: Bearer {token}`
3. 验证响应状态码为 200

**预期结果**: 返回文档列表

**状态**: ⏸️ BLOCKED - 等待 token 获取

---

### TC-006: Token 认证 - 无 Token
**前置条件**: 无
**测试步骤**:
1. 不带 token 发送 GET 请求到 `/api/v1/documents`
2. 验证响应状态码为 403
3. 验证响应包含错误信息

**预期结果**:
```json
{
  "ok": false,
  "code": "FORBIDDEN",
  "message": "无权限访问此资源"
}
```

**状态**: ⚠️ PARTIAL - 403 返回空响应体

---

## 2. 文档模块测试用例

### TC-101: 文档树 - 无认证
**前置条件**: 无
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/documents/tree`
2. 不携带 token
3. 验证响应

**预期结果**: 返回 403 或未授权错误

**状态**: ⚠️ PARTIAL - 403 返回空响应体

---

### TC-102: 文档树 - 有认证
**前置条件**: 已登录，拥有有效 token
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/documents/tree`
2. 携带有效 token
3. 验证响应状态码为 200
4. 验证返回树形结构数据

**预期结果**:
```json
{
  "ok": true,
  "data": [
    { "id": "...", "type": "folder", "name": "...", "children": [...] }
  ]
}
```

**状态**: ⏸️ BLOCKED - 等待 token

---

### TC-103: 文档列表 - 分页
**前置条件**: 已登录
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/documents?limit=10&offset=0`
2. 验证响应包含分页信息

**预期结果**:
```json
{
  "ok": true,
  "data": {
    "items": [...],
    "total": 100,
    "limit": 10,
    "offset": 0
  }
}
```

**状态**: ⏸️ BLOCKED

---

### TC-104: 文档详情 - 获取单个文档
**前置条件**: 已登录，文档存在
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/documents/{docId}`
2. 验证响应包含文档完整信息

**预期结果**:
```json
{
  "ok": true,
  "data": {
    "docId": "...",
    "title": "...",
    "content": "...",
    ...
  }
}
```

**状态**: ⏸️ BLOCKED

---

## 3. 仪表盘模块测试用例

### TC-201: 健康检查
**前置条件**: 无
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/admin/health`
2. 验证响应状态码为 200
3. 验证各组件状态

**预期结果**:
```json
{
  "status": "UP",
  "components": {
    "database": { "status": "UP" },
    "storage": { "status": "UP" },
    "memory": { "status": "UP" }
  }
}
```

**状态**: ✅ PASS (已验证)

---

### TC-202: 系统指标 - 无认证
**前置条件**: 无
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/admin/metrics`
2. 不携带 token
3. 验证响应

**预期结果**: 返回 403

**状态**: ⚠️ PARTIAL - 403 返回空响应体

---

### TC-203: 系统指标 - 有认证
**前置条件**: 已登录，拥有管理员权限
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/admin/metrics`
2. 携带管理员 token
3. 验证响应包含系统指标

**预期结果**:
```json
{
  "ok": true,
  "data": {
    "totalDocuments": 150,
    "totalChunks": 5000,
    ...
  }
}
```

**状态**: ⏸️ BLOCKED

---

### TC-204: 告警列表
**前置条件**: 已登录
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/admin/alerts`
2. 携带有效 token
3. 验证响应

**预期结果**: 返回告警列表

**状态**: ⏸️ BLOCKED

---

## 4. 导入模块测试用例

### TC-301: 创建导入任务
**前置条件**: 已登录
**测试步骤**:
1. 发送 POST 请求到 `/api/v1/documents/import`
2. 携带文件数据
3. 验证返回任务 ID

**状态**: ⏸️ BLOCKED

---

### TC-302: 查询导入状态
**前置条件**: 已有导入任务
**测试步骤**:
1. 发送 GET 请求到 `/api/v1/documents/import/{taskId}`
2. 验证返回任务状态

**状态**: ⏸️ BLOCKED

---

## 测试执行汇总

| 模块 | 用例数 | PASS | FAIL | BLOCKED | PARTIAL |
|------|--------|------|------|---------|---------|
| 认证 | 6 | 2 | 1 | 2 | 1 |
| 文档 | 4 | 0 | 0 | 3 | 1 |
| 仪表盘 | 4 | 1 | 0 | 3 | 1 |
| 导入 | 2 | 0 | 0 | 2 | 0 |
| **总计** | **16** | **3** | **1** | **10** | **3** |

---

## 问题列表

| ID | 问题描述 | 严重程度 | 负责模块 |
|----|----------|----------|----------|
| P1 | 注册接口返回 INTERNAL_ERROR | HIGH | 后端 |
| P2 | 403 响应无 body | MEDIUM | 后端 |
