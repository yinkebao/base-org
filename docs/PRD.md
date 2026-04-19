# PRD

## 1. 产品定位

面向企业内部知识管理场景，提供文档导入、文档检索问答、文档详情浏览与内联编辑能力。

## 2. 用户与场景

- 普通用户：检索知识、查看文档、编辑自己有权限的文档
- 管理员：查看系统指标、告警与健康状态
- 文档维护者：导入文档、维护目录、更新文档内容

## 3. 关键能力

### 3.1 文档导入

- 支持本地上传、URL、S3、Confluence 作为来源入口
- 记录导入状态、进度、失败原因
- 导入完成后落库到文档表并触发后续分块/向量化链路

### 3.2 文档管理

- 文档树与列表
- 文档详情渲染
- 原地内联编辑
- 图片上传与文档资源访问

### 3.3 检索与 QA

- 用户输入自然语言问题
- 系统返回 AI 回答与来源片段
- 敏感来源无权限时必须被过滤并给出提示

### 3.4 系统仪表盘

- 展示系统指标
- 展示最近导入任务
- 展示运行告警

## 4. 数据模型摘要

```typescript
interface Document {
  doc_id: string;
  title: string;
  owner_id: string;
  status: "draft" | "published" | "archived";
  sensitivity: "public" | "internal" | "confidential" | "secret";
  content_markdown: string;
}

interface Chunk {
  chunk_id: string;
  doc_id: string;
  chunk_index: number;
  text: string;
  embedding: number[];
  metadata: Record<string, any>;
}

interface ImportTask {
  task_id: string;
  owner_id: string;
  filename: string;
  status: string;
  progress: number;
  result_doc_id?: string;
}

interface AuditLog {
  audit_id: string;
  user_id: string;
  action: string;
  resource_type: string;
  resource_id: string;
  details: Record<string, any>;
}
```

## 5. API 摘要

| 端点 | 方法 | 描述 |
|-----|------|------|
| `/api/v1/auth/login` | POST | 登录 |
| `/api/v1/auth/register` | POST | 注册 |
| `/api/v1/documents/import` | POST | 导入文档 |
| `/api/v1/documents/import/{taskId}` | GET | 查询导入状态 |
| `/api/v1/documents` | GET | 获取文档列表 |
| `/api/v1/documents/{id}` | GET | 获取文档详情 |
| `/api/v1/documents/{id}` | PUT | 更新文档 |
| `/api/v1/qa` | POST | 检索问答 |
| `/api/v1/admin/metrics` | GET | 获取系统指标 |
| `/api/v1/admin/alerts` | GET | 获取告警列表 |
| `/api/v1/admin/health` | GET | 健康检查 |

## 6. 验收标准

### 6.1 功能验收

- [ ] 可成功导入 PDF/Word/Markdown 文档
- [ ] 文档树、详情和编辑链路可正常工作
- [ ] 检索结果召回率 ≥ 80%
- [ ] AI 回答正确率 ≥ 85%

### 6.2 性能验收

- [ ] 50 并发下 P95 延迟 ≤ 3s
- [ ] 向量 DB 支持 10M+ chunk 检索

### 6.3 安全验收

- [ ] 未授权用户无法检索敏感文档
- [ ] 审计日志完整记录 365 天
- [ ] Token 超预算触发告警
