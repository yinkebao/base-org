# API 契约文档

> 前后端协作确认的 API 规范
> 最后更新：2026-03-26

## 一、统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "trace_20260326_xxx"
}
```

## 二、核心接口

### 2.1 认证

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `GET /api/v1/auth/check-username`
- `GET /api/v1/auth/sso/start`

### 2.2 QA 检索

- `POST /api/v1/qa`

请求体示例：

```json
{
  "query": "如何配置向量数据库？",
  "filters": {
    "dept": "产品研发部",
    "sensitivity": ["public", "internal"]
  },
  "topK": 5
}
```

### 2.3 文档导入

- `POST /api/v1/documents/import`
- `GET /api/v1/documents/import/{taskId}`
- `POST /api/v1/documents/import/{taskId}/cancel`
- `GET /api/v1/documents/import/recent`

状态值：

- `pending`
- `processing`
- `done`
- `failed`
- `cancelled`

### 2.4 文档管理

- `GET /api/v1/documents/tree`
- `GET /api/v1/documents`
- `GET /api/v1/documents/{docId}`
- `PUT /api/v1/documents/{docId}`
- `GET /api/v1/documents/{docId}/breadcrumb`
- `POST /api/v1/documents/{docId}/assets/images`
- `GET /api/v1/documents/{docId}/assets/images/{filename}`

### 2.5 仪表盘

- `GET /api/v1/admin/metrics`
- `GET /api/v1/admin/alerts`
- `GET /api/v1/admin/health`

`/api/v1/admin/metrics` 返回后端聚合结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "documents": {
      "totalDocuments": 0,
      "publishedDocuments": 0,
      "draftDocuments": 0,
      "totalChunks": 0,
      "weeklyGrowth": 0,
      "byType": {}
    },
    "imports": {
      "totalTasks": 0,
      "completedTasks": 0,
      "processingTasks": 0,
      "failedTasks": 0,
      "successRate": 0,
      "avgProcessingTime": 0
    },
    "storage": {
      "usedSpace": 0,
      "totalSpace": 0,
      "usagePercent": 0,
      "usedSpaceFormatted": "0B",
      "totalSpaceFormatted": "0B"
    },
    "system": {
      "cpuUsage": 0,
      "memoryUsage": 0,
      "totalMemory": 0,
      "usedMemory": 0,
      "freeMemory": 0,
      "javaVersion": "17",
      "osName": "macOS",
      "uptime": 0
    },
    "timestamp": 0
  },
  "traceId": "trace_xxx"
}
```

## 三、错误码

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_NOT_ACTIVATED`
- `AUTH_LOCKED`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `USERNAME_TAKEN`
- `EMAIL_TAKEN`
- `PASSWORD_WEAK`
- `USER_NOT_FOUND`
- `DOC_NOT_FOUND`
- `DOC_ACCESS_DENIED`
- `DOC_VERSION_CONFLICT`
- `DOC_IMAGE_INVALID_TYPE`
- `DOC_IMAGE_TOO_LARGE`
- `IMPORT_TASK_NOT_FOUND`
- `IMPORT_TASK_FINALIZED`
- `IMPORT_FILE_TOO_LARGE`
- `IMPORT_PARSE_ERROR`
- `QA_QUERY_EMPTY`
- `QA_NO_RESULTS`
- `QA_LLM_TIMEOUT`
- `QA_RATE_LIMITED`
- `QA_SENSITIVE_FILTERED`
- `TEMPLATE_NOT_FOUND`
- `TEMPLATE_VARIABLE_MISSING`
- `INTERNAL_ERROR`
- `INVALID_PARAMETER`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`

## 四、范围说明

当前契约仅覆盖认证、文档导入、文档管理、检索问答与管理监控接口。
