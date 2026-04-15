import { getDb, saveDb } from "./mock-db.js";
import { validatePassword } from "../../utils/validators.js";

function traceId() {
  return `mock_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
}

function wait(ms = 200) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toBase64(arrayBuffer) {
  if (typeof Buffer !== "undefined") {
    return Buffer.from(arrayBuffer).toString("base64");
  }

  let binary = "";
  const bytes = new Uint8Array(arrayBuffer);
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

function ok(data) {
  return { ok: true, data, traceId: traceId() };
}

function fail(code, message, details = null) {
  return { ok: false, code, message, details, traceId: traceId() };
}

function createToken(userId) {
  return `mock-token-${userId}-${Date.now()}`;
}

const IMPORT_STAGES = [
  { status: "PENDING", progress: 5, stageMessage: "任务排队中", at: 1000 },
  { status: "PARSING", progress: 30, stageMessage: "文档解析中", at: 3500 },
  { status: "CHUNKING", progress: 50, stageMessage: "文本分块中", at: 5200 },
  { status: "EMBEDDING", progress: 75, stageMessage: "向量生成中", at: 7000 },
  { status: "STORING", progress: 90, stageMessage: "索引写入中", at: 8600 },
  { status: "COMPLETED", progress: 100, stageMessage: "导入完成", at: Infinity }
];

function buildTreeMap(nodes) {
  const map = new Map();
  nodes.forEach((node) => {
    map.set(node.id, node);
  });
  return map;
}

function findFolderNode(db, folderId) {
  return db.documentTreeNodes.find((node) => node.id === folderId && node.type === "folder") || null;
}

function buildFolderPathData(db, folderId) {
  const map = buildTreeMap(db.documentTreeNodes);
  const folders = [];
  const visited = new Set();
  let current = map.get(folderId);

  while (current && current.type === "folder" && !visited.has(current.id)) {
    visited.add(current.id);
    folders.unshift(current);
    current = current.parentId ? map.get(current.parentId) : null;
  }

  return {
    folderPathIds: folders.map((item) => item.id),
    folderPathNames: folders.map((item) => item.name)
  };
}

function fallbackDept(deptFilter) {
  const value = String(deptFilter || "").trim();
  if (!value || value === "全部部门") {
    return "技术研发部";
  }
  return value;
}

function resolveInheritedDocument(db, folderId) {
  const map = buildTreeMap(db.documentTreeNodes);
  let currentFolderId = folderId;
  const visited = new Set();

  while (currentFolderId && !visited.has(currentFolderId)) {
    visited.add(currentFolderId);
    const matched = db.documents.find((doc) => Array.isArray(doc.folderPathIds) && doc.folderPathIds.includes(currentFolderId));
    if (matched) {
      return matched;
    }
    const currentFolder = map.get(currentFolderId);
    currentFolderId = currentFolder?.parentId || "";
  }

  return null;
}

function updateImportTaskByTime(task) {
  if (task.status === "COMPLETED" || task.status === "FAILED" || task.status === "CANCELLED") {
    return task;
  }
  const elapsed = Date.now() - task.createdAt;
  const stage = IMPORT_STAGES.find((item) => elapsed <= item.at) || IMPORT_STAGES[IMPORT_STAGES.length - 1];
  task.status = stage.status;
  task.progress = stage.progress;
  task.stageMessage = stage.stageMessage;

  if (task.failAt && task.status === task.failAt) {
    task.status = "FAILED";
    task.stageMessage = "导入失败";
    task.errorMessage = task.errorMessage || "解析异常，请检查文档格式与内容";
  }

  if (task.status === "COMPLETED" && !task.resultDocId) {
    task.resultDocId = `DOC-${task.id}`;
  }

  return task;
}

function refreshImportTasks(db) {
  db.importTasks.forEach((task) => updateImportTaskByTime(task));
}

function toRecentImportItem(task) {
  return {
    taskId: task.id,
    filename: task.name,
    fileType: task.type,
    progress: task.progress,
    status: task.status
  };
}

function findUserByIdentity(users, identity) {
  const value = String(identity || "").trim().toLowerCase();
  return users.find((user) => user.email.toLowerCase() === value || user.username.toLowerCase() === value);
}

function sanitizeUser(user) {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    role: user.role,
    active: user.active
  };
}

export async function login(payload) {
  await wait();
  const db = getDb();
  const user = findUserByIdentity(db.users, payload.identity);

  if (!user) {
    return fail("AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
  }

  if (!user.active) {
    return fail("AUTH_NOT_ACTIVATED", "请先激活您的账户，检查注册邮箱");
  }

  if (user.lockedUntil && user.lockedUntil > Date.now()) {
    return fail("AUTH_LOCKED", "账户已锁定，请联系管理员");
  }

  if (user.password !== payload.password) {
    user.failedCount += 1;
    if (user.failedCount >= 5) {
      user.lockedUntil = Date.now() + 15 * 60 * 1000;
      saveDb(db);
      return fail("AUTH_LOCKED", "账户已锁定，请联系管理员");
    }
    saveDb(db);
    return fail("AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
  }

  user.failedCount = 0;
  user.lockedUntil = null;
  saveDb(db);

  return ok({
    user: sanitizeUser(user),
    token: createToken(user.id)
  });
}

export async function checkUsername(payload) {
  await wait(120);
  const db = getDb();
  const username = String(payload.username || "").trim().toLowerCase();
  const exists = db.users.some((user) => user.username.toLowerCase() === username);
  if (exists) {
    return fail("USERNAME_TAKEN", "该用户名已被占用");
  }
  return ok({ available: true });
}

export async function register(payload) {
  await wait();
  const db = getDb();
  const normalizedEmail = String(payload.email || "").trim().toLowerCase();
  const normalizedUsername = String(payload.username || "").trim().toLowerCase();

  const usernameExists = db.users.some((user) => user.username.toLowerCase() === normalizedUsername);
  if (usernameExists) {
    return fail("USERNAME_TAKEN", "用户名已存在");
  }

  const emailExists = db.users.some((user) => user.email.toLowerCase() === normalizedEmail);
  if (emailExists) {
    return fail("EMAIL_TAKEN", "邮箱已被注册");
  }

  if (!validatePassword(payload.password)) {
    return fail("PASSWORD_WEAK", "密码复杂度不足");
  }

  const user = {
    id: `u_${Math.random().toString(36).slice(2, 10)}`,
    username: payload.username.trim(),
    email: normalizedEmail,
    password: payload.password,
    role: "pm",
    active: true,
    failedCount: 0,
    lockedUntil: null
  };
  db.users.push(user);
  saveDb(db);

  return ok({
    user: sanitizeUser(user),
    token: createToken(user.id)
  });
}

export async function startSso() {
  await wait(80);
  return ok({
    redirectUrl: "/dashboard.html#overview?sso=1"
  });
}

export async function getMetrics() {
  await wait(150);
  const db = getDb();
  return ok(db.metrics);
}

export async function getRecentImports() {
  await wait(180);
  const db = getDb();
  refreshImportTasks(db);
  db.importTasks.sort((a, b) => b.createdAt - a.createdAt);
  saveDb(db);
  return ok(db.importTasks.slice(0, 10).map(toRecentImportItem));
}

export async function getAlerts() {
  await wait(180);
  const db = getDb();
  return ok(db.alerts);
}

export async function createImportTask(payload) {
  await wait(180);
  if (payload?.sourceType && payload.sourceType !== "upload") {
    return fail("INVALID_PARAMETER", "当前仅支持本地文件上传");
  }
  if (!payload?.file && !payload?.fileMeta?.name) {
    return fail("INVALID_PARAMETER", "文件不能为空");
  }

  const db = getDb();
  const id = `TSK-${String(db.importTaskSequence).padStart(5, "0")}`;
  db.importTaskSequence += 1;

  const fileName = payload?.file?.name || payload?.fileMeta?.name || "本地导入任务";
  const chunkConfig = payload?.chunkConfig || {};
  const task = {
    id,
    name: fileName,
    type: String(fileName || "FILE").split(".").pop().toUpperCase(),
    sourceType: "upload",
    metadata: {
      sourceType: "upload",
      versionLabel: payload?.versionLabel || "v1.0.0",
      tags: Array.isArray(payload?.tags) ? payload.tags : [],
      contentType: payload?.file?.type || payload?.fileMeta?.type || "",
      originalFilename: fileName
    },
    chunkConfig,
    status: "PENDING",
    progress: 0,
    stageMessage: "任务排队中",
    createdAt: Date.now(),
    failAt: null,
    cancelled: false,
    errorMessage: "",
    processedChunks: 0,
    totalChunks: 0,
    resultDocId: null
  };
  db.importTasks.unshift(task);
  saveDb(db);

  return ok({
    taskId: task.id,
    status: task.status,
    progress: task.progress,
    totalChunks: task.totalChunks,
    processedChunks: task.processedChunks,
    resultDocId: task.resultDocId,
    errorMessage: task.errorMessage
  });
}

export async function getImportTaskStatus(taskId) {
  await wait(150);
  const db = getDb();
  const task = db.importTasks.find((item) => item.id === taskId);
  if (!task) {
    return fail("IMPORT_TASK_NOT_FOUND", "任务不存在");
  }

  updateImportTaskByTime(task);
  saveDb(db);

  return ok({
    taskId: task.id,
    status: task.status,
    progress: task.progress,
    totalChunks: task.totalChunks,
    processedChunks: task.processedChunks,
    resultDocId: task.resultDocId,
    errorMessage: task.errorMessage || ""
  });
}

export async function cancelImportTask(taskId) {
  await wait(120);
  const db = getDb();
  const task = db.importTasks.find((item) => item.id === taskId);
  if (!task) {
    return fail("IMPORT_TASK_NOT_FOUND", "任务不存在");
  }
  if (task.status === "COMPLETED" || task.status === "FAILED" || task.status === "CANCELLED") {
    return fail("IMPORT_TASK_FINALIZED", "任务已结束，无法取消");
  }

  task.status = "CANCELLED";
  task.stageMessage = "任务已取消";
  task.cancelled = true;
  saveDb(db);

  return ok({
    taskId: task.id,
    status: task.status,
    progress: task.progress,
    totalChunks: task.totalChunks,
    processedChunks: task.processedChunks,
    resultDocId: task.resultDocId,
    errorMessage: task.errorMessage || ""
  });
}

export async function getDocumentTree(dept = "") {
  await wait(120);
  const db = getDb();
  const normalizedDept = String(dept || "").trim();
  const docs = normalizedDept && normalizedDept !== "全部部门" ? db.documents.filter((doc) => doc.dept === normalizedDept) : db.documents;
  const allowedDocIds = new Set(docs.map((doc) => doc.docId));
  const nodeById = buildTreeMap(db.documentTreeNodes);
  const visibleNodeIds = new Set();

  db.documentTreeNodes.forEach((node) => {
    if (node.type !== "doc" || !allowedDocIds.has(node.docId)) return;
    visibleNodeIds.add(node.id);
    let current = node;
    while (current?.parentId) {
      const parent = nodeById.get(current.parentId);
      if (!parent) break;
      visibleNodeIds.add(parent.id);
      current = parent;
    }
  });

  const nodes = db.documentTreeNodes.filter((node) => visibleNodeIds.has(node.id));
  return ok({
    departments: db.departments,
    nodes
  });
}

export async function getDocumentList(params = {}) {
  await wait(140);
  const db = getDb();
  const dept = String(params.dept || "").trim();
  const keyword = String(params.keyword || "").trim().toLowerCase();
  const folderId = String(params.folderId || "").trim();

  let docs = [...db.documents];
  if (dept && dept !== "全部部门") {
    docs = docs.filter((doc) => doc.dept === dept);
  }
  if (keyword) {
    docs = docs.filter((doc) => {
      const target = `${doc.title} ${doc.folderPathNames.join(" ")} ${doc.dept}`.toLowerCase();
      return target.includes(keyword);
    });
  }
  if (folderId) {
    docs = docs.filter((doc) => doc.folderPathIds.includes(folderId));
  }

  return ok(
    docs.map((doc) => ({
      docId: doc.docId,
      title: doc.title,
      dept: doc.dept,
      visibility: doc.visibility,
      scope: doc.scope,
      status: doc.status,
      updatedBy: doc.updatedBy,
      updatedAt: doc.updatedAt,
      folderPathIds: doc.folderPathIds,
      folderPathNames: doc.folderPathNames
    }))
  );
}

export async function getDocumentDetail(docId) {
  await wait(140);
  const db = getDb();
  const doc = db.documents.find((item) => item.docId === docId);
  if (!doc) {
    return fail("DOC_NOT_FOUND", "文档不存在");
  }
  return ok(doc);
}

export async function getDocumentBreadcrumb(docId) {
  await wait(100);
  const db = getDb();
  const doc = db.documents.find((item) => item.docId === docId);
  if (!doc) {
    return fail("DOC_NOT_FOUND", "文档不存在");
  }
  const nodeMap = buildTreeMap(db.documentTreeNodes);
  const folders = doc.folderPathIds
    .map((folderId) => nodeMap.get(folderId))
    .filter(Boolean)
    .map((folder) => ({ id: folder.id, name: folder.name, type: "folder" }));
  return ok([
    ...folders,
    { id: doc.docId, name: doc.title, type: "doc" }
  ]);
}

export async function createDocument(folderId, payload = {}) {
  await wait(180);
  const db = getDb();
  const folder = findFolderNode(db, folderId);
  if (!folder) {
    return fail("DOC_FOLDER_NOT_FOUND", "目录不存在");
  }

  const inheritedDoc = resolveInheritedDocument(db, folderId);
  const pathData = buildFolderPathData(db, folderId);
  const docId = `doc-generated-${String(db.documentSequence).padStart(5, "0")}`;
  db.documentSequence += 1;

  const document = {
    docId,
    title: "未命名文档",
    dept: inheritedDoc?.dept || fallbackDept(payload.deptFilter),
    visibility: inheritedDoc?.visibility || "受限",
    scope: inheritedDoc?.scope || "内部文档",
    status: "已发布",
    updatedBy: "当前用户",
    updatedAt: "刚刚",
    version: 1,
    folderPathIds: pathData.folderPathIds,
    folderPathNames: pathData.folderPathNames,
    contentMarkdown: ""
  };
  const treeNode = {
    id: `doc-node-${docId}`,
    name: document.title,
    type: "doc",
    parentId: folderId,
    childrenIds: [],
    docId
  };

  db.documents.unshift(document);
  db.documentTreeNodes.push(treeNode);
  folder.childrenIds = [...(folder.childrenIds || []), treeNode.id];
  saveDb(db);

  return ok({
    ...document,
    treeNodeId: treeNode.id
  });
}

export async function updateDocument(docId, payload = {}) {
  await wait(180);
  const db = getDb();
  const doc = db.documents.find((item) => item.docId === docId);
  if (!doc) {
    return fail("DOC_NOT_FOUND", "文档不存在");
  }

  const nextTitle = String(payload.title || doc.title).trim() || doc.title;
  const nextMarkdown = String(payload.contentMarkdown || "");

  doc.title = nextTitle;
  doc.contentMarkdown = nextMarkdown;
  if (payload.dept) {
    doc.dept = String(payload.dept).trim() || doc.dept;
  }
  doc.updatedAt = "刚刚";
  doc.updatedBy = String(payload.updatedBy || "当前用户").trim() || "当前用户";
  doc.version = Number(doc.version || 1) + 1;

  const treeNode = db.documentTreeNodes.find((node) => node.type === "doc" && node.docId === docId);
  if (treeNode) {
    treeNode.name = nextTitle;
  }

  saveDb(db);
  return ok({
    ...doc,
    version: doc.version,
    savedAt: doc.updatedAt
  });
}

export async function uploadDocumentImage(docId, file, alt = "") {
  await wait(120);
  const db = getDb();
  const doc = db.documents.find((item) => item.docId === docId);
  if (!doc) {
    return fail("DOC_NOT_FOUND", "文档不存在");
  }
  if (!file) {
    return fail("DOC_IMAGE_REQUIRED", "请选择图片文件");
  }

  const name = String(file.name || "image.png").trim() || "image.png";
  const type = String(file.type || "").trim().toLowerCase();
  if (!type.startsWith("image/")) {
    return fail("DOC_IMAGE_INVALID_TYPE", "仅支持上传图片文件");
  }

  const arrayBuffer = typeof file.arrayBuffer === "function" ? await file.arrayBuffer() : new ArrayBuffer(0);
  const base64 = toBase64(arrayBuffer);
  const dataUrl = `data:${type || "image/png"};base64,${base64}`;

  return ok({
    url: dataUrl,
    alt: String(alt || "").trim(),
    filename: name,
    size: Number(file.size || 0)
  });
}

// QA 检索相关 mock 数据
const QA_MOCK_ANSWERS = [
  {
    keywords: ["向量", "数据库", "pgvector", "配置"],
    answer: "向量数据库配置需要以下步骤：\n\n1. **安装 pgvector 扩展**：在 PostgreSQL 中执行 `CREATE EXTENSION vector;`\n\n2. **创建向量列**：使用 `embedding vector(1536)` 定义向量字段\n\n3. **创建 HNSW 索引**：`CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops);`\n\n4. **配置连接池**：建议设置 `max_connections=100` 和 `shared_buffers=256MB`\n\n具体参数需根据数据量和查询频率调整。",
    sources: [
      { docId: "doc_001", docTitle: "向量数据库配置指南", score: 0.95 },
      { docId: "doc_002", docTitle: "PostgreSQL 性能优化", score: 0.88 }
    ]
  },
  {
    keywords: ["认证", "登录", "JWT", "token", "安全"],
    answer: "系统采用 JWT 认证机制：\n\n1. **登录流程**：用户提交用户名/邮箱和密码，验证通过后返回 access_token\n\n2. **Token 有效期**：access_token 有效期 2 小时，refresh_token 有效期 7 天\n\n3. **权限控制**：基于角色的访问控制（RBAC），支持 admin/pm/dev 角色\n\n4. **安全措施**：密码使用 bcrypt 加密，登录失败 5 次锁定 15 分钟",
    sources: [
      { docId: "doc_003", docTitle: "认证系统设计", score: 0.92 },
      { docId: "doc_004", docTitle: "安全规范", score: 0.85 }
    ]
  },
  {
    keywords: ["导入", "文档", "上传", "分块", "chunk"],
    answer: "文档导入支持以下方式：\n\n1. **本地上传**：支持 PDF、Word、Markdown、TXT 格式，单文件最大 200MB\n\n2. **URL 抓取**：输入网页 URL，系统自动抓取并解析内容\n\n3. **S3 存储**：支持从 S3 存储桶导入文档\n\n4. **Confluence**：支持从 Confluence 空间导入页面\n\n分块配置支持自定义 Chunk 大小（100-2000 tokens）和 Overlap（0-500 tokens）。",
    sources: [
      { docId: "doc_005", docTitle: "文档导入指南", score: 0.94 },
      { docId: "doc_006", docTitle: "分块策略", score: 0.87 }
    ]
  },
  {
    keywords: ["文档", "编辑", "保存", "内联"],
    answer: "文档编辑采用原地内联模式：\n\n1. **进入编辑态**：在文档详情页直接切换到编辑模式\n\n2. **自动保存**：编辑过程中会按节奏触发保存状态更新\n\n3. **手动保存**：用户可以显式点击保存按钮完成提交\n\n4. **资源插入**：支持图片与富文本内容插入\n\n5. **版本保护**：保存请求会携带版本信息，避免并发覆盖",
    sources: [
      { docId: "doc_007", docTitle: "文档编辑规范", score: 0.91 }
    ]
  }
];

function findMockAnswer(query) {
  const lowerQuery = query.toLowerCase();
  for (const item of QA_MOCK_ANSWERS) {
    const matched = item.keywords.some((kw) => lowerQuery.includes(kw));
    if (matched) {
      return item;
    }
  }
  return null;
}

export async function queryQA(payload) {
  await wait(800);
  const query = String(payload?.query || "").trim();

  if (!query) {
    return fail("VALIDATION_ERROR", "请输入查询内容");
  }

  const db = getDb();
  const matched = findMockAnswer(query);

  if (!matched) {
    return ok({
      answer: "抱歉，我在知识库中没有找到与您问题相关的内容。请尝试更换关键词或联系管理员添加相关文档。",
      sources: [],
      promptHash: `sha256:${Math.random().toString(36).slice(2, 10)}`,
      filteredCount: 0
    });
  }

  // 模拟构建完整的 sources 数据
  const sources = matched.sources.map((src, index) => {
    const doc = db.documents.find((d) => d.docId === src.docId);
    return {
      chunkId: `chunk_${index + 1}`,
      docId: src.docId,
      docTitle: src.docTitle,
      text: doc?.content?.slice(0, 200) || "相关文档片段...",
      score: src.score,
      metadata: {
        dept: doc?.dept || "产品研发部",
        sensitivity: doc?.visibility || "internal"
      }
    };
  });

  // 模拟敏感数据过滤
  const filteredCount = query.includes("敏感") ? 1 : 0;

  return ok({
    answer: matched.answer,
    sources,
    promptHash: `sha256:${Math.random().toString(36).slice(2, 10)}`,
    filteredCount
  });
}

export async function getQAHistory(params = {}) {
  await wait(150);
  const page = Number(params.page) || 1;
  const size = Math.min(Number(params.size) || 20, 50);

  // 返回模拟历史记录
  const items = [
    {
      id: "qa_001",
      query: "如何配置向量数据库？",
      answer: "向量数据库配置需要以下步骤...",
      createdAt: "2026-03-22T08:30:00Z"
    },
    {
      id: "qa_002",
      query: "认证系统是如何工作的？",
      answer: "系统采用 JWT 认证机制...",
      createdAt: "2026-03-22T08:15:00Z"
    }
  ];

  return ok({
    items,
    total: items.length,
    page,
    size,
    hasMore: false
  });
}
