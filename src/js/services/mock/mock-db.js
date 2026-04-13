const STORAGE_KEY = "base-org-mock-db-v1";

function createMemoryStorage() {
  const store = new Map();
  return {
    getItem(key) {
      return store.has(key) ? store.get(key) : null;
    },
    setItem(key, value) {
      store.set(key, String(value));
    },
    removeItem(key) {
      store.delete(key);
    }
  };
}

const memoryStorage = createMemoryStorage();

function getStorage() {
  if (typeof window !== "undefined" && window.localStorage) {
    return window.localStorage;
  }
  return memoryStorage;
}

function createDefaultDb() {
  const now = Date.now();
  return {
    users: [
      {
        id: "u_admin",
        username: "admin",
        email: "admin@system.com",
        password: "Admin@123",
        role: "admin",
        active: true,
        failedCount: 0,
        lockedUntil: null
      },
      {
        id: "u_locked",
        username: "locked",
        email: "locked@system.com",
        password: "Locked@123",
        role: "pm",
        active: true,
        failedCount: 5,
        lockedUntil: Date.now() + 15 * 60 * 1000
      },
      {
        id: "u_inactive",
        username: "inactive",
        email: "inactive@system.com",
        password: "Inactive@123",
        role: "pm",
        active: false,
        failedCount: 0,
        lockedUntil: null
      }
    ],
    metrics: {
      tokenToday: 842901,
      tokenGrowthPercent: 12.5,
      vectorDbStatus: "健康状态",
      vectorDbHealth: "NORMAL",
      llmAvailability: "100%",
      llmStability: "稳定",
      processingTasks: 12
    },
    importTaskSequence: 130,
    documentSequence: 100,
    importTasks: [
      {
        id: "TSK-00129",
        name: "2024Q3 需求文档导入",
        type: "PDF",
        sourceType: "upload",
        status: "STORING",
        progress: 85,
        stageMessage: "索引写入中",
        createdAt: now - 1000 * 60 * 20,
        metadata: { dept: "产品研发部", sensitivity: "CONFIDENTIAL", tags: ["需求文档"], version: "v1.0.0" },
        chunkConfig: { size: 500, overlap: 100, structuredChunk: true, ocr: false },
        failAt: null,
        cancelled: false
      },
      {
        id: "TSK-00128",
        name: "技术规范说明书",
        type: "DOCX",
        sourceType: "upload",
        status: "COMPLETED",
        progress: 100,
        stageMessage: "导入完成",
        createdAt: now - 1000 * 60 * 55,
        metadata: { dept: "架构组", sensitivity: "PUBLIC", tags: ["规范"], version: "v2.1.0" },
        chunkConfig: { size: 500, overlap: 100, structuredChunk: true, ocr: false },
        failAt: null,
        cancelled: false
      },
      {
        id: "TSK-00127",
        name: "竞品分析报告",
        type: "Spider",
        sourceType: "url",
        status: "FAILED",
        progress: 40,
        stageMessage: "解析失败",
        createdAt: now - 1000 * 60 * 80,
        metadata: { dept: "市场策略部", sensitivity: "CONFIDENTIAL", tags: ["竞品"], version: "v1.2.0" },
        chunkConfig: { size: 500, overlap: 100, structuredChunk: true, ocr: false },
        failAt: "PARSING",
        cancelled: false,
        errorMessage: "页面抓取超时，请检查源站可达性"
      },
      {
        id: "TSK-00126",
        name: "接口文档汇编",
        type: "MD",
        sourceType: "upload",
        status: "COMPLETED",
        progress: 100,
        stageMessage: "导入完成",
        createdAt: now - 1000 * 60 * 120,
        metadata: { dept: "平台组", sensitivity: "PUBLIC", tags: ["接口文档"], version: "v3.0.0" },
        chunkConfig: { size: 500, overlap: 100, structuredChunk: true, ocr: false },
        failAt: null,
        cancelled: false
      }
    ],
    departments: ["全部部门", "技术研发部", "平台组", "产品设计部", "产品管理部", "市场运营部"],
    documentTreeNodes: [
      {
        id: "folder-kb",
        name: "知识库",
        type: "folder",
        parentId: null,
        childrenIds: ["folder-tech", "folder-ui", "folder-product"]
      },
      {
        id: "folder-tech",
        name: "技术规格",
        type: "folder",
        parentId: "folder-kb",
        childrenIds: ["folder-platform", "doc-arch-v24", "doc-security-protocol"]
      },
      {
        id: "folder-platform",
        name: "平台工程",
        type: "folder",
        parentId: "folder-tech",
        childrenIds: ["doc-api-gateway", "doc-observability"]
      },
      {
        id: "folder-ui",
        name: "UI 规范",
        type: "folder",
        parentId: "folder-kb",
        childrenIds: ["doc-ui-token", "doc-rich-ui-playbook"]
      },
      {
        id: "folder-product",
        name: "产品需求",
        type: "folder",
        parentId: "folder-kb",
        childrenIds: ["doc-prd-template"]
      },
      { id: "folder-meeting", name: "会议记录", type: "folder", parentId: null, childrenIds: ["doc-meeting-202603"] },
      { id: "doc-node-arch-v24", name: "架构设计（当前）", type: "doc", parentId: "folder-tech", childrenIds: [], docId: "doc-arch-v24" },
      { id: "doc-node-security-protocol", name: "安全协议", type: "doc", parentId: "folder-tech", childrenIds: [], docId: "doc-security-protocol" },
      { id: "doc-node-api-gateway", name: "API Gateway 设计规范", type: "doc", parentId: "folder-platform", childrenIds: [], docId: "doc-api-gateway" },
      { id: "doc-node-observability", name: "可观测性接入手册", type: "doc", parentId: "folder-platform", childrenIds: [], docId: "doc-observability" },
      { id: "doc-node-ui-token", name: "Design Token 指南", type: "doc", parentId: "folder-ui", childrenIds: [], docId: "doc-ui-token" },
      {
        id: "doc-node-rich-ui-playbook",
        name: "富文本组件展示手册（Mock）",
        type: "doc",
        parentId: "folder-ui",
        childrenIds: [],
        docId: "doc-rich-ui-playbook"
      },
      { id: "doc-node-prd-template", name: "PRD 标准模板", type: "doc", parentId: "folder-product", childrenIds: [], docId: "doc-prd-template" },
      { id: "doc-node-meeting-202603", name: "03 月架构周会纪要", type: "doc", parentId: "folder-meeting", childrenIds: [], docId: "doc-meeting-202603" }
    ],
    documents: [
      {
        docId: "doc-arch-v24",
        title: "Azure Logic 架构核心规范 v2.4",
        dept: "技术研发部",
        visibility: "高敏感度",
        scope: "内部文档",
        status: "已发布",
        updatedBy: "王小明",
        updatedAt: "2小时前",
        folderPathIds: ["folder-kb", "folder-tech"],
        folderPathNames: ["知识库", "技术规格"],
        contentMarkdown: `> 本文档旨在定义 Azure Logic 设计系统的核心技术选型、组件通讯机制及全局状态管理策略。所有新开发的模块必须严格遵守本架构规范。

## 1. 系统组件模型

系统采用微前端架构，各子应用之间通过定制的事件总线进行通信。核心库依赖于 Tailwind CSS v3 提供的实用工具类，确保视觉表现的原子化和高性能渲染。

关键技术指标：

- 响应式断点：\`sm:640px, md:768px, lg:1024px\`
- 首屏加载性能优化目标：**< 1.5s (LCP)**
- 组件库覆盖率：_当前已达 92%_

![服务器架构图](https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=1400&q=80)

图 1.1：全局数据中心分布示意图

## 2. 性能基准测试

| 测试项 | 基准值 | 当前实测 | 状态 |
| --- | --- | --- | --- |
| 路由跳转延迟 | 200ms | 145ms | 优 |
| 大文件渲染速度 | 800ms | 920ms | 待优化 |
| 内存占用 | < 150MB | 112MB | 优 |

## 3. 开发提示

在实施组件嵌套时，务必调用 \`useAzureLogic()\` Hook 来保持主题上下文的实时同步。这对于暗色模式切换至关重要。`
      },
      {
        docId: "doc-security-protocol",
        title: "安全协议",
        dept: "技术研发部",
        visibility: "受限",
        scope: "内部文档",
        status: "已发布",
        updatedBy: "李思思",
        updatedAt: "1天前",
        folderPathIds: ["folder-kb", "folder-tech"],
        folderPathNames: ["知识库", "技术规格"],
        contentMarkdown: `# 安全协议

## 访问控制

采用 RBAC + 数据级 ACL 组合，所有访问操作写入审计日志。

## 传输安全

- 全链路 TLS
- 密钥托管于 KMS
- 定期轮换访问令牌`
      },
      {
        docId: "doc-ui-token",
        title: "Design Token 指南",
        dept: "产品设计部",
        visibility: "公开",
        scope: "团队可见",
        status: "已发布",
        updatedBy: "陈立",
        updatedAt: "3天前",
        folderPathIds: ["folder-kb", "folder-ui"],
        folderPathNames: ["知识库", "UI 规范"],
        contentMarkdown: `# Design Token 指南

## 颜色系统

\`\`\`css
:root {
  --color-primary: #2463eb;
  --color-surface: #ffffff;
}
\`\`\`

## 间距与排版

- spacing 使用 4px 递增阶梯
- 字体采用 Inter 与中文系统字体回退`
      },
      {
        docId: "doc-rich-ui-playbook",
        title: "富文本组件展示手册（Mock）",
        dept: "产品设计部",
        visibility: "公开",
        scope: "团队可见",
        status: "已发布",
        updatedBy: "林清雅",
        updatedAt: "刚刚",
        folderPathIds: ["folder-kb", "folder-ui"],
        folderPathNames: ["知识库", "UI 规范"],
        contentMarkdown: `# 富文本组件展示手册（Mock）

> 本文档用于演示“文档详情页”在 mock 接口下的富格式内容渲染能力，包括标题、引用、表格、代码块、任务清单、图片与链接。

## 1. 文本与强调

这是一个演示段落，包含 **加粗**、*斜体*、~~删除线~~ 和 \`行内代码\`。

### 1.1 信息块

> 提示：组件文档应同时提供设计说明、交互约束和可访问性规范。
>
> 若涉及权限字段，必须注明可见范围与脱敏策略。

## 2. 列表与任务清单

- 设计目标：统一文档阅读体验
- 技术目标：兼容长文档与大表格横向滚动
- 质量目标：关键路径可测、可回归

1. 完成页面结构拆分
2. 接入 mock 服务层
3. 接入真实 API 时只替换 service adapter

- [x] 支持标题层级与目录导航
- [x] 支持代码块与表格
- [ ] 支持评论与协作标注（后续迭代）

## 3. 代码示例

\`\`\`js
const payload = {
  sourceType: "upload",
  metadata: { dept: "产品设计部", sensitivity: "internal" },
  chunkConfig: { size: 500, overlap: 100 }
};

async function createTask(service) {
  const resp = await service.createImportTask(payload);
  if (!resp.ok) throw new Error(resp.message);
  return resp.data.taskId;
}
\`\`\`

\`\`\`bash
curl -X POST /api/v1/documents/import \\
  -H "Content-Type: application/json" \\
  -d '{"sourceType":"upload","metadata":{"dept":"产品设计部"}}'
\`\`\`

## 4. 参数表格

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| sourceType | string | 是 | upload/url/s3/confluence |
| metadata.dept | string | 是 | 文档归属部门 |
| metadata.sensitivity | string | 是 | public/internal/confidential/secret |
| chunkConfig.size | number | 否 | 分块大小，默认 500 |
| chunkConfig.overlap | number | 否 | 重叠大小，默认 100 |

## 5. 图片与链接

![页面结构示意图](https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1200&q=80)

可参考 [设计系统规范](https://example.com/design-system) 与 [前端工程约定](https://example.com/frontend-guideline)。

## 6. JSON 示例

\`\`\`json
{
  "ok": true,
  "data": {
    "docId": "doc-rich-ui-playbook",
    "title": "富文本组件展示手册（Mock）",
    "updatedAt": "刚刚"
  },
  "traceId": "mock_20260320_xxxx"
}
\`\`\`
`
      },
      {
        docId: "doc-api-gateway",
        title: "API Gateway 设计规范",
        dept: "技术研发部",
        visibility: "受限",
        scope: "内部文档",
        status: "已发布",
        updatedBy: "孙浩",
        updatedAt: "6小时前",
        folderPathIds: ["folder-kb", "folder-tech", "folder-platform"],
        folderPathNames: ["知识库", "技术规格", "平台工程"],
        contentMarkdown: `# API Gateway 设计规范

## 目标

统一鉴权、限流、路由与审计能力，提供稳定的北向 API 接入层。

## 关键规则

- 所有请求必须携带 trace-id
- 生产环境默认开启限流与熔断
- 管理端接口按 RBAC 做权限校验`
      },
      {
        docId: "doc-observability",
        title: "可观测性接入手册",
        dept: "平台组",
        visibility: "公开",
        scope: "团队可见",
        status: "已发布",
        updatedBy: "张辰",
        updatedAt: "1天前",
        folderPathIds: ["folder-kb", "folder-tech", "folder-platform"],
        folderPathNames: ["知识库", "技术规格", "平台工程"],
        contentMarkdown: `# 可观测性接入手册

## 指标

- API P95 延迟
- 错误率
- token 消耗

## 日志规范

统一输出 JSON 日志，字段至少包含 \`traceId\`、\`userId\`、\`action\`。`
      },
      {
        docId: "doc-prd-template",
        title: "PRD 标准模板",
        dept: "产品管理部",
        visibility: "公开",
        scope: "团队可见",
        status: "编辑中",
        updatedBy: "周明",
        updatedAt: "2天前",
        folderPathIds: ["folder-kb", "folder-product"],
        folderPathNames: ["知识库", "产品需求"],
        contentMarkdown: `# PRD 标准模板

## 1. 背景

说明业务背景、目标用户与问题定义。

## 2. 需求范围

- In Scope
- Out of Scope

## 3. 验收标准

至少包含功能验收、性能验收与安全验收。`
      },
      {
        docId: "doc-meeting-202603",
        title: "03 月架构周会纪要",
        dept: "平台组",
        visibility: "公开",
        scope: "团队可见",
        status: "编辑中",
        updatedBy: "赵天宇",
        updatedAt: "5天前",
        folderPathIds: ["folder-meeting"],
        folderPathNames: ["会议记录"],
        contentMarkdown: `# 03 月架构周会纪要

## 决议

1. 导入向导本周进入提测
2. 文档详情页支持 markdown 富文本
3. 下周评审 QA 工作区交互稿`
      }
    ],
    alerts: [
      {
        id: "alt_01",
        level: "critical",
        title: "API 响应超时 (408)",
        description: "集群节点 node-04 响应时间超过 5000ms，触发自动容错机制。",
        actions: ["限流熔断", "详情"],
        ago: "29 分钟前"
      },
      {
        id: "alt_02",
        level: "warning",
        title: "向量索引负载过高",
        description: "CPU 占用率达到 85%，建议分片或扩容。",
        actions: ["任务卸载", "忽略"],
        ago: "15 分钟前"
      },
      {
        id: "alt_03",
        level: "info",
        title: "系统自动快照完成",
        description: "备份 ID BKUP-20240905-04，存储至 S3 区域。",
        actions: [],
        ago: "1 小时前"
      }
    ]
  };
}

function mergeUnique(values = []) {
  return Array.from(new Set(values.filter((item) => String(item || "").trim())));
}

function ensureMockDocumentSeeds(db) {
  const seed = createDefaultDb();

  db.departments = mergeUnique([...(db.departments || []), ...(seed.departments || [])]);

  const nodeById = new Map((db.documentTreeNodes || []).map((node) => [node.id, node]));
  for (const seedNode of seed.documentTreeNodes || []) {
    if (!nodeById.has(seedNode.id)) {
      const cloned = {
        ...seedNode,
        childrenIds: Array.isArray(seedNode.childrenIds) ? [...seedNode.childrenIds] : []
      };
      db.documentTreeNodes.push(cloned);
      nodeById.set(cloned.id, cloned);
      continue;
    }
    const current = nodeById.get(seedNode.id);
    if (current.type === "folder") {
      current.childrenIds = mergeUnique([...(current.childrenIds || []), ...(seedNode.childrenIds || [])]);
    }
  }

  const docById = new Map((db.documents || []).map((doc) => [doc.docId, doc]));
  for (const seedDoc of seed.documents || []) {
    if (!docById.has(seedDoc.docId)) {
      db.documents.push({ ...seedDoc });
      continue;
    }
    const current = docById.get(seedDoc.docId);
    if (!current.contentMarkdown || String(current.contentMarkdown).trim().length === 0) {
      current.contentMarkdown = seedDoc.contentMarkdown;
    }
  }
}

function normalizeDbSchema(db) {
  if (!db.importTaskSequence) {
    db.importTaskSequence = 130;
  }
  if (!db.documentSequence) {
    db.documentSequence = 100;
  }
  if (!Array.isArray(db.importTasks)) {
    const recentImports = Array.isArray(db.recentImports) ? db.recentImports : [];
    db.importTasks = recentImports.map((item, index) => ({
      id: item.id || `TSK-00${index + 1}`,
      name: item.name || "未命名任务",
      type: item.type || "FILE",
      sourceType: "upload",
      status: item.status === "已完成" ? "COMPLETED" : item.status === "异常" ? "FAILED" : "STORING",
      progress: Number(item.progress || 0),
      stageMessage: item.status || "处理中",
      createdAt: Date.now() - (index + 1) * 1000 * 60 * 10,
      metadata: { dept: "未指定", sensitivity: "PUBLIC", tags: [], version: "v1.0.0" },
      chunkConfig: { size: 500, overlap: 100, structuredChunk: true, ocr: false },
      failAt: null,
      cancelled: false
    }));
  }
  if (!Array.isArray(db.departments)) {
    db.departments = ["全部部门", "技术研发部", "平台组", "产品设计部", "市场运营部"];
  }
  if (!Array.isArray(db.documentTreeNodes)) {
    db.documentTreeNodes = [];
  }
  if (!Array.isArray(db.documents)) {
    db.documents = [];
  }

  ensureMockDocumentSeeds(db);
  return db;
}

export function getDb() {
  const storage = getStorage();
  const raw = storage.getItem(STORAGE_KEY);
  if (!raw) {
    const seed = normalizeDbSchema(createDefaultDb());
    storage.setItem(STORAGE_KEY, JSON.stringify(seed));
    return seed;
  }
  try {
    const parsed = JSON.parse(raw);
    const normalized = normalizeDbSchema(parsed);
    storage.setItem(STORAGE_KEY, JSON.stringify(normalized));
    return normalized;
  } catch (_error) {
    const seed = normalizeDbSchema(createDefaultDb());
    storage.setItem(STORAGE_KEY, JSON.stringify(seed));
    return seed;
  }
}

export function saveDb(db) {
  const storage = getStorage();
  storage.setItem(STORAGE_KEY, JSON.stringify(db));
}

export function clearDb() {
  const storage = getStorage();
  storage.removeItem(STORAGE_KEY);
}
