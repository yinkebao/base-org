# 前后端 API 契约文档

## 通用响应格式

### 成功响应
```json
{
  "ok": true,
  "data": { ... },
  "traceId": "trace_xxx"
}
```

### 错误响应
```json
{
  "ok": false,
  "code": "ERROR_CODE",
  "message": "用户友好的错误描述",
  "details": { ... },  // 可选
  "traceId": "trace_xxx",
  "status": 400        // HTTP 状态码（可选）
}
```

---

## 认证模块 API

### POST /api/v1/auth/login

**请求：**
```json
{
  "identity": "username or email",
  "password": "password"
}
```

**成功响应（200）：**
```json
{
  "ok": true,
  "data": {
    "user": {
      "id": "user-id",
      "username": "username",
      "email": "user@example.com",
      "dept": "department",
      "role": "role"
    },
    "token": "jwt-token-string"
  },
  "traceId": "trace_xxx"
}
```

**错误响应：**
| 状态码 | code | message |
|--------|------|---------|
| 400 | VALIDATION_ERROR | 参数校验失败 |
| 401 | AUTH_INVALID_CREDENTIALS | 用户名或密码错误 |
| 403 | AUTH_NOT_ACTIVATED | 请先激活您的账户 |
| 403 | AUTH_LOCKED | 账户已锁定，请联系管理员 |

---

### POST /api/v1/auth/register

**请求：**
```json
{
  "username": "newuser",
  "password": "Password@123",
  "email": "newuser@example.com"
}
```

**成功响应（200）：**
```json
{
  "ok": true,
  "data": {
    "user": {
      "id": "new-user-id",
      "username": "newuser",
      "email": "newuser@example.com"
    },
    "token": "jwt-token-string"
  },
  "traceId": "trace_xxx"
}
```

**错误响应：**
| 状态码 | code | message |
|--------|------|---------|
| 400 | VALIDATION_ERROR | 参数校验失败 |
| 409 | USERNAME_TAKEN | 该用户名已被占用 |
| 409 | EMAIL_TAKEN | 该邮箱已被注册 |

---

### GET /api/v1/auth/check-username?username=xxx

**请求参数：**
- `username`: 要检查的用户名

**成功响应（200）：**
```json
{
  "ok": true,
  "data": {
    "username": "xxx",
    "available": true,
    "message": "用户名可用"
  },
  "traceId": "trace_xxx"
}
```

**不可用响应（200）：**
```json
{
  "ok": true,
  "data": {
    "username": "xxx",
    "available": false,
    "message": "该用户名已被占用"
  },
  "traceId": "trace_xxx"
}
```

---

## 文档模块 API

### GET /api/v1/documents/tree

**请求参数：**
- `dept`: 部门筛选（可选）

**成功响应（200）：**
```json
{
  "ok": true,
  "data": [
    {
      "id": "node-id",
      "type": "folder",
      "name": "文件夹名称",
      "parentId": null,
      "children": [ ... ]
    }
  ],
  "traceId": "trace_xxx"
}
```

**错误响应（403）：**
```json
{
  "ok": false,
  "code": "FORBIDDEN",
  "message": "无权限访问此资源",
  "traceId": "trace_xxx"
}
```

---

### GET /api/v1/documents

**请求参数：**
- `limit`: 每页数量（默认 20）
- `offset`: 偏移量（默认 0）
- `dept`: 部门筛选（可选）

**成功响应（200）：**
```json
{
  "ok": true,
  "data": {
    "items": [ ... ],
    "total": 100,
    "limit": 20,
    "offset": 0
  },
  "traceId": "trace_xxx"
}
```

---

### GET /api/v1/documents/{id}

**成功响应（200）：**
```json
{
  "ok": true,
  "data": {
    "docId": "doc-id",
    "title": "文档标题",
    "content": "文档内容（HTML 或 Markdown）",
    "sourceUrl": "https://...",
    "ownerId": "owner-id",
    "dept": "部门",
    "version": 1,
    "sensitivity": "internal",
    "createdAt": "2026-03-26T00:00:00Z",
    "updatedAt": "2026-03-26T00:00:00Z"
  },
  "traceId": "trace_xxx"
}
```

---

## 仪表盘模块 API

### GET /api/v1/admin/health

**成功响应（200）：**
```json
{
  "status": "UP",
  "timestamp": 1774516559237,
  "components": {
    "database": {
      "status": "UP",
      "message": "Database connection is healthy"
    },
    "storage": {
      "status": "UP",
      "message": "Storage is accessible",
      "details": {
        "freeSpace": "20.5 GB",
        "path": "/path/to/storage"
      }
    },
    "memory": {
      "status": "UP",
      "message": "Memory usage: 2.0%",
      "details": {
        "maxMemory": "4.0 GB",
        "usagePercent": 1.98,
        "usedMemory": "81.0 MB"
      }
    }
  }
}
```

---

### GET /api/v1/admin/metrics

**请求头：**
```
Authorization: Bearer {token}
```

**成功响应（200）：**
```json
{
  "ok": true,
  "data": {
    "totalDocuments": 150,
    "totalChunks": 5000,
    "totalUsers": 25,
    "importTasksToday": 10,
    "avgQueryTime": 250
  },
  "traceId": "trace_xxx"
}
```

---

### GET /api/v1/admin/alerts

**请求头：**
```
Authorization: Bearer {token}
```

**成功响应（200）：**
```json
{
  "ok": true,
  "data": [
    {
      "id": "alert-1",
      "level": "warning",
      "title": "存储空间不足",
      "message": "可用存储空间低于 10%",
      "createdAt": "2026-03-26T00:00:00Z"
    }
  ],
  "traceId": "trace_xxx"
}
```

---

## Token 机制

### 存储位置
- localStorage key: `base-org-session-v1`

### 存储格式
```json
{
  "user": { ... },
  "token": "jwt-token-string"
}
```

### 使用方式
每次请求自动在请求头中添加：
```
Authorization: Bearer {token}
```

### 401 处理
当收到 401 响应时，自动清除 session 并跳转登录页。

---

## 错误码常量

| 错误码 | 数值 | 描述 |
|--------|------|------|
| UNAUTHORIZED | 1001 | 未授权，请登录 |
| TOKEN_EXPIRED | 1002 | 登录已过期，请重新登录 |
| FORBIDDEN | 1003 | 无权限访问此资源 |
| NOT_FOUND | 2001 | 请求的资源不存在 |
| VALIDATION_ERROR | 3001 | 参数校验失败 |
| SENSITIVE_FILTERED | 4001 | 部分内容因敏感已被过滤 |
| LLM_TIMEOUT | 4002 | AI 响应超时，请稍后重试 |
| RATE_LIMITED | 4003 | 请求过于频繁，请稍后重试 |
| INTERNAL_ERROR | 5001 | 系统繁忙，请稍后重试 |
