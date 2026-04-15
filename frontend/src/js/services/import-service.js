import { API_ENDPOINTS } from "../config/api-endpoints.js";
import { isMockMode } from "../config/env.js";
import { request } from "./http-client.js";
import * as MockApi from "./mock/mock-api.js";

const STATUS_LABELS = {
  PENDING: "任务排队中",
  PARSING: "文档解析中",
  CHUNKING: "文本分块中",
  EMBEDDING: "向量生成中",
  STORING: "索引写入中",
  COMPLETED: "导入完成",
  FAILED: "导入失败",
  CANCELLED: "任务已取消"
};

function normalizeStatus(status) {
  const raw = String(status || "").trim().toUpperCase();
  if (!raw) return "PENDING";
  if (raw === "DONE") return "COMPLETED";
  if (raw === "QUEUED" || raw === "UPLOADING") return "PENDING";
  if (raw === "INDEXING") return "STORING";
  return raw;
}

function statusLabel(status) {
  const normalized = normalizeStatus(status);
  return STATUS_LABELS[normalized] || normalized;
}

function normalizeImportTask(item) {
  if (!item || typeof item !== "object") {
    return null;
  }

  const normalizedStatus = normalizeStatus(item.status);
  return {
    taskId: item.taskId ?? item.id ?? "",
    name: item.name ?? item.filename ?? "未命名任务",
    filename: item.filename ?? item.name ?? "未命名任务",
    type: item.type ?? item.fileType ?? "UNKNOWN",
    fileType: item.fileType ?? item.type ?? "UNKNOWN",
    progress: Number.isFinite(item.progress) ? item.progress : 0,
    status: normalizedStatus,
    statusLabel: item.stageMessage || item.statusLabel || statusLabel(normalizedStatus),
    errorMessage: item.errorMessage || item.error || "",
    totalChunks: Number.isFinite(item.totalChunks) ? item.totalChunks : 0,
    processedChunks: Number.isFinite(item.processedChunks) ? item.processedChunks : 0,
    resultDocId: item.resultDocId ?? null
  };
}

function normalizeRecentImportItem(item) {
  const normalized = normalizeImportTask(item);
  if (!normalized) {
    return null;
  }

  return {
    id: normalized.taskId,
    name: normalized.name,
    type: normalized.type,
    progress: normalized.progress,
    status: normalized.status,
    statusLabel: normalized.statusLabel
  };
}

function normalizeRecentImportsPayload(payload) {
  const items = Array.isArray(payload) ? payload : payload?.tasks;
  if (!Array.isArray(items)) {
    return [];
  }
  return items.map(normalizeRecentImportItem).filter(Boolean);
}

function buildImportFormData(payload = {}) {
  const formData = new FormData();
  formData.append("file", payload.file);
  formData.append("sensitivity", String(payload.sensitivity || ""));
  formData.append("versionLabel", String(payload.versionLabel || ""));
  formData.append("chunkSize", String(payload.chunkConfig?.size ?? ""));
  formData.append("chunkOverlap", String(payload.chunkConfig?.overlap ?? ""));
  formData.append("structuredChunk", String(Boolean(payload.chunkConfig?.structuredChunk)));

  if (payload.parentId !== undefined && payload.parentId !== null && payload.parentId !== "") {
    formData.append("parentId", String(payload.parentId));
  }
  if (payload.description) {
    formData.append("description", payload.description);
  }
  const tags = Array.isArray(payload.tags) ? payload.tags : [];
  tags.forEach((tag) => {
    if (String(tag || "").trim()) {
      formData.append("tags", String(tag).trim());
    }
  });

  return formData;
}

export const ImportService = {
  async createImportTask(payload) {
    if (isMockMode()) {
      const response = await MockApi.createImportTask(payload);
      return response.ok
        ? {
            ...response,
            data: normalizeImportTask(response.data)
          }
        : response;
    }
    const response = await request(API_ENDPOINTS.imports.createTask, {
      method: "POST",
      data: buildImportFormData(payload)
    });
    return response.ok
      ? {
          ...response,
          data: normalizeImportTask(response.data)
        }
      : response;
  },

  async getImportTaskStatus(taskId) {
    if (isMockMode()) {
      const response = await MockApi.getImportTaskStatus(taskId);
      return response.ok
        ? {
            ...response,
            data: normalizeImportTask(response.data)
          }
        : response;
    }
    const response = await request(`${API_ENDPOINTS.imports.taskStatusBase}/${taskId}`, {
      method: "GET"
    });
    return response.ok
      ? {
          ...response,
          data: normalizeImportTask(response.data)
        }
      : response;
  },

  async cancelImportTask(taskId) {
    if (isMockMode()) {
      const response = await MockApi.cancelImportTask(taskId);
      return response.ok
        ? {
            ...response,
            data: normalizeImportTask(response.data)
          }
        : response;
    }
    const response = await request(
      `${API_ENDPOINTS.imports.taskStatusBase}/${taskId}${API_ENDPOINTS.imports.cancelSuffix}`,
      {
        method: "POST"
      }
    );
    return response.ok
      ? {
          ...response,
          data: normalizeImportTask(response.data)
        }
      : response;
  },

  async getRecentImports() {
    if (isMockMode()) {
      const response = await MockApi.getRecentImports();
      return response.ok
        ? {
            ...response,
            data: normalizeRecentImportsPayload(response.data)
          }
        : response;
    }
    const response = await request(API_ENDPOINTS.imports.recent, {
      method: "GET"
    });
    return response.ok
      ? {
          ...response,
          data: normalizeRecentImportsPayload(response.data)
        }
      : response;
  }
};
