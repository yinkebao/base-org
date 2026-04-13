import { API_ENDPOINTS } from "../config/api-endpoints.js";
import { isMockMode } from "../config/env.js";
import { request } from "./http-client.js";
import * as MockApi from "./mock/mock-api.js";

function formatRelativeTime(timestamp) {
  if (!timestamp) {
    return "刚刚";
  }

  const time = new Date(timestamp).getTime();
  if (Number.isNaN(time)) {
    return "刚刚";
  }

  const diffMs = Date.now() - time;
  const diffMinutes = Math.max(0, Math.floor(diffMs / 60000));

  if (diffMinutes < 1) return "刚刚";
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} 小时前`;

  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays} 天前`;
}

function normalizeMetricsPayload(payload) {
  if (!payload || typeof payload !== "object") {
    return {
      tokenToday: 0,
      tokenGrowthPercent: 0,
      vectorDbStatus: "未知",
      vectorDbHealth: "UNKNOWN",
      llmAvailability: "未知",
      llmStability: "未知",
      processingTasks: 0
    };
  }

  if ("tokenToday" in payload) {
    return payload;
  }

  const storage = payload.storage || {};
  const system = payload.system || {};
  const imports = payload.imports || {};

  return {
    tokenToday: Number(storage.usedSpace || 0),
    tokenGrowthPercent: Number(imports.successRate || 0),
    vectorDbStatus: `${Number(payload.documents?.totalChunks || 0).toLocaleString()} chunks`,
    vectorDbHealth: Number(system.cpuUsage || 0) < 80 ? "NORMAL" : "WARN",
    llmAvailability: `${Math.max(0, 100 - Number(imports.failedTasks || 0))}%`,
    llmStability: Number(system.memoryUsage || 0) < 80 ? "稳定" : "波动",
    processingTasks: Number(imports.processingTasks || 0)
  };
}

function normalizeAlertLevel(type) {
  switch (String(type || "").toUpperCase()) {
    case "CRITICAL":
      return "critical";
    case "WARNING":
      return "warning";
    default:
      return "info";
  }
}

function normalizeAlertsPayload(payload) {
  const items = Array.isArray(payload) ? payload : payload?.alerts;
  if (!Array.isArray(items)) {
    return [];
  }

  return items.map((item) => ({
    id: item.id,
    level: item.level || normalizeAlertLevel(item.type),
    title: item.title || "未命名告警",
    description: item.description || item.message || "",
    actions: Array.isArray(item.actions) ? item.actions : [],
    ago: item.ago || formatRelativeTime(item.timestamp)
  }));
}

export const DashboardService = {
  async getMetrics() {
    if (isMockMode()) {
      const response = await MockApi.getMetrics();
      return response.ok
        ? {
            ...response,
            data: normalizeMetricsPayload(response.data)
          }
        : response;
    }
    const response = await request(API_ENDPOINTS.dashboard.metrics, { method: "GET" });
    return response.ok
      ? {
          ...response,
          data: normalizeMetricsPayload(response.data)
        }
      : response;
  },

  async getRecentImports() {
    if (isMockMode()) {
      return MockApi.getRecentImports();
    }
    return request(API_ENDPOINTS.dashboard.recentImports, { method: "GET" });
  },

  async getAlerts() {
    if (isMockMode()) {
      const response = await MockApi.getAlerts();
      return response.ok
        ? {
            ...response,
            data: normalizeAlertsPayload(response.data)
          }
        : response;
    }
    const response = await request(API_ENDPOINTS.dashboard.alerts, { method: "GET" });
    return response.ok
      ? {
          ...response,
          data: normalizeAlertsPayload(response.data)
        }
      : response;
  }
};
