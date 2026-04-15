/**
 * QA 检索服务层
 * @see docs/API_CONTRACT.md
 */

import { request } from "./http-client.js";
import { API_BASE_URL, API_MODE } from "../config/env.js";
import * as mockApi from "./mock/mock-api.js";

function normalizeSources(sources = []) {
  return sources.map((src) => {
    const chunkId = src.chunkId ?? src.chunk_id ?? "";
    const docId = src.docId ?? src.doc_id ?? "";
    const docTitle = src.docTitle ?? src.title ?? "";
    const content = src.content ?? "";
    const score = src.score ?? 0;
    const metadata = src.metadata || {
      dept: "技术研发部",
      sensitivity: "internal"
    };

    return {
      chunkId: String(chunkId),
      docId: String(docId),
      docTitle,
      content,
      score,
      metadata
    };
  });
}

function mapSession(session) {
  return {
    sessionId: String(session.sessionId ?? session.id ?? ""),
    title: session.title || "新对话",
    lastMessageAt: session.lastMessageAt || session.updatedAt || session.createdAt || null,
    createdAt: session.createdAt || null
  };
}

function mapMessage(message) {
  return {
    id: String(message.messageId ?? message.id ?? ""),
    role: String(message.role || "").toLowerCase(),
    content: message.content || "",
    rewrittenQuery: message.rewrittenQuery || "",
    sources: normalizeSources(message.sources || []),
    confidence: message.confidence ?? 0,
    promptHash: message.promptHash || "",
    intent: message.intent || "",
    degraded: Boolean(message.degraded),
    degradeReason: message.degradeReason || "",
    fallbackMode: message.fallbackMode || "",
    status: message.status || "COMPLETED",
    errorCode: message.errorCode || "",
    processingTimeMs: message.processingTimeMs ?? 0,
    planSummary: message.planSummary || "",
    toolTrace: Array.isArray(message.toolTrace) ? message.toolTrace : [],
    diagrams: Array.isArray(message.diagrams) ? message.diagrams : [],
    externalSources: Array.isArray(message.externalSources) ? message.externalSources : [],
    timestamp: message.createdAt || new Date().toISOString()
  };
}

function resolveWebSocketUrl(ticket) {
  const baseUrl = API_BASE_URL || (typeof window !== "undefined" ? window.location.origin : "http://localhost:8080");
  const url = new URL(baseUrl, typeof window !== "undefined" ? window.location.origin : "http://localhost:8080");
  const protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${url.host}/ws/qa?ticket=${encodeURIComponent(ticket)}`;
}

/**
 * 执行 QA 检索查询
 * @param {Object} payload 查询参数
 * @param {string} payload.question 查询文本（后端使用 question 字段）
 * @param {Object} [payload.filters] 过滤条件
 * @param {number} [payload.topK=5] 返回结果数量
 * @param {number} [payload.scoreThreshold=0.7] 相似度阈值
 * @param {boolean} [payload.includeSources=true] 是否返回来源
 * @returns {Promise<{ok: boolean, data?: Object, message?: string}>}
 */
export async function query(payload) {
  if (API_MODE === "mock") {
    return mockApi.queryQA(payload);
  }

  // 转换前端 payload 到后端请求格式
  const requestData = {
    question: payload.question || payload.query, // 兼容旧字段名
    sessionId: payload.sessionId ? Number(payload.sessionId) : undefined,
    topK: payload.topK || 5,
    scoreThreshold: payload.scoreThreshold ?? 0.7,
    includeSources: payload.includeSources !== false,
    stream: false, // MVP 阶段不支持流式
    webSearchEnabled: payload.webSearchEnabled === true
  };

    const result = await request("/api/v1/qa", {
      method: "POST",
      data: requestData,
      timeout: 30000 // QA 查询可能较慢
    });

    // 后端响应字段映射到前端期望格式
    if (result.ok && result.data) {
      result.data.sources = normalizeSources(result.data.sources || []);
      result.data.sessionId = String(result.data.sessionId ?? payload.sessionId ?? "");
      result.data.messageId = String(result.data.messageId ?? "");
      result.data.intent = result.data.intent || "";
      result.data.degraded = Boolean(result.data.degraded);
      result.data.degradeReason = result.data.degradeReason || "";
      result.data.fallbackMode = result.data.fallbackMode || "";
      result.data.planSummary = result.data.planSummary || "";
      result.data.toolTrace = Array.isArray(result.data.toolTrace) ? result.data.toolTrace : [];
      result.data.diagrams = Array.isArray(result.data.diagrams) ? result.data.diagrams : [];
      result.data.externalSources = Array.isArray(result.data.externalSources) ? result.data.externalSources : [];
    }

  return result;
}

/**
 * 创建 QA 会话
 */
export async function createSession() {
  if (API_MODE === "mock") {
    return {
      ok: true,
      data: mapSession({
        sessionId: `mock-${Date.now()}`,
        title: "新对话",
        createdAt: new Date().toISOString()
      })
    };
  }
  const result = await request("/api/v1/qa/sessions", { method: "POST" });
  if (result.ok && result.data) {
    result.data = mapSession(result.data);
  }
  return result;
}

export async function listSessions() {
  if (API_MODE === "mock") {
    return { ok: true, data: [] };
  }
  const result = await request("/api/v1/qa/sessions", { method: "GET" });
  if (result.ok) {
    result.data = Array.isArray(result.data) ? result.data.map(mapSession) : [];
  }
  return result;
}

export async function getSessionMessages(sessionId) {
  if (API_MODE === "mock") {
    return { ok: true, data: [] };
  }
  const result = await request(`/api/v1/qa/sessions/${encodeURIComponent(sessionId)}/messages`, { method: "GET" });
  if (result.ok) {
    result.data = Array.isArray(result.data) ? result.data.map(mapMessage) : [];
  }
  return result;
}

export async function createWebSocketTicket() {
  if (API_MODE === "mock") {
    return { ok: true, data: { ticket: `mock-ticket-${Date.now()}` } };
  }
  return request("/api/v1/qa/ws-ticket", { method: "POST" });
}

/**
 * 流式 QA 查询（WebSocket）
 * @param {Object} payload 查询参数
 * @param {function} onChunk 接收数据块的回调
 * @param {function} onSources 接收来源的回调
 * @param {function} onDone 完成回调
 * @param {function} onError 错误回调
 * @returns {function} 取消函数
 */
export function queryStream(payload, { onChunk, onSources, onDone, onError, onPlan, onToolStart, onToolResult, onToolError, onDiagram }) {
  if (API_MODE === "mock") {
    query(payload).then((result) => {
      if (!result.ok) {
        onError?.(result.message || "查询失败");
        return;
      }
      onChunk?.(result.data.answer || "");
      onSources?.(result.data.sources || []);
      onDone?.(result.data);
    });
    return () => {};
  }

  let socket;
  let closed = false;
  let answer = "";

  const cancel = () => {
    closed = true;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.close();
    }
  };

  createWebSocketTicket()
    .then((ticketResult) => {
      if (!ticketResult.ok || !ticketResult.data?.ticket) {
        onError?.(ticketResult.message || "无法建立实时连接");
        return;
      }

      socket = new WebSocket(resolveWebSocketUrl(ticketResult.data.ticket));
      socket.addEventListener("message", (event) => {
        if (closed) return;
        const data = JSON.parse(event.data);
        if (data.type === "plan") {
          onPlan?.({
            planSummary: data.planSummary || "",
            toolPlan: data.toolPlan || null
          });
          return;
        }
        if (data.type === "tool_start") {
          onToolStart?.(data.trace || null);
          return;
        }
        if (data.type === "tool_result") {
          onToolResult?.(data.trace || null);
          return;
        }
        if (data.type === "tool_error") {
          onToolError?.({
            toolId: data.toolId || "",
            stepId: data.stepId || "",
            message: data.message || ""
          });
          return;
        }
        if (data.type === "diagram") {
          onDiagram?.(data.diagram || null);
          return;
        }
        if (data.type === "sources") {
          onSources?.(normalizeSources(data.sources || []));
          return;
        }
        if (data.type === "delta") {
          answer += data.content || "";
          onChunk?.(answer);
          return;
        }
        if (data.type === "done") {
          onDone?.({
            ...data,
            answer: data.answer || answer,
            sessionId: String(data.sessionId ?? payload.sessionId ?? ""),
            sources: normalizeSources(data.sources || []),
            degraded: Boolean(data.degraded),
            degradeReason: data.degradeReason || "",
            intent: data.intent || "",
            fallbackMode: data.fallbackMode || "",
            planSummary: data.planSummary || "",
            toolTrace: Array.isArray(data.toolTrace) ? data.toolTrace : [],
            diagrams: Array.isArray(data.diagrams) ? data.diagrams : [],
            externalSources: Array.isArray(data.externalSources) ? data.externalSources : []
          });
          socket.close();
          return;
        }
        if (data.type === "error") {
          onError?.(data.message || "查询失败", data);
          socket.close();
        }
      });
      socket.addEventListener("error", () => {
        if (!closed) {
          onError?.("实时连接失败");
        }
      });
      socket.addEventListener("open", () => {
        socket.send(JSON.stringify({
          type: "ask",
          sessionId: payload.sessionId ? Number(payload.sessionId) : null,
          messageId: payload.messageId || `client_${Date.now()}`,
          question: payload.question || payload.query || "",
          webSearchEnabled: payload.webSearchEnabled === true
        }));
      });
    })
    .catch((error) => {
      onError?.(error.message || "网络错误");
    });

  return cancel;
}

export const QAService = {
  query,
  createSession,
  listSessions,
  getSessionMessages,
  createWebSocketTicket,
  queryStream
};

export default QAService;
