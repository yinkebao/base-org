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
      pendingTasks: 12
    },
    recentImports: [
      { id: "TSK-00129", name: "2024Q3 需求文档导入", type: "PDF", progress: 85, status: "处理中" },
      { id: "TSK-00128", name: "技术规范说明书", type: "DOCX", progress: 100, status: "已完成" },
      { id: "TSK-00127", name: "竞品分析报告", type: "Spider", progress: 40, status: "异常" },
      { id: "TSK-00126", name: "接口文档汇编", type: "MD", progress: 100, status: "已完成" }
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

export function getDb() {
  const storage = getStorage();
  const raw = storage.getItem(STORAGE_KEY);
  if (!raw) {
    const seed = createDefaultDb();
    storage.setItem(STORAGE_KEY, JSON.stringify(seed));
    return seed;
  }
  try {
    return JSON.parse(raw);
  } catch (_error) {
    const seed = createDefaultDb();
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
