import { API_BASE_URL } from "../config/env.js";
import { getSession } from "./session-service.js";

function buildHeaders(extraHeaders = {}) {
  const headers = {
    ...extraHeaders
  };

  if (!("Content-Type" in headers)) {
    headers["Content-Type"] = "application/json";
  }

  // 注入 Authorization header
  const session = getSession();
  if (session?.token) {
    headers["Authorization"] = `Bearer ${session.token}`;
  }

  return headers;
}

function resolveUrl(path) {
  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }
  return `${API_BASE_URL}${path}`;
}

function createTraceId() {
  return `trace_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function mapFetchError(error) {
  if (error.name === "AbortError") {
    return {
      ok: false,
      code: "REQUEST_TIMEOUT",
      message: "请求超时，请稍后重试"
    };
  }
  return {
    ok: false,
    code: "NETWORK_ERROR",
    message: "网络异常，请检查连接"
  };
}

function resolveApiError(responseData) {
  if (!responseData || typeof responseData !== "object") return null;

  if (typeof responseData.success === "boolean" && responseData.success === false) {
    return {
      code: String(responseData.code || "API_ERROR"),
      message: responseData.message || "请求失败"
    };
  }

  if (typeof responseData.code === "string") {
    const normalizedCode = responseData.code.toUpperCase();
    if (normalizedCode && !["SUCCESS", "OK"].includes(normalizedCode)) {
      return {
        code: responseData.code,
        message: responseData.message || "请求失败"
      };
    }
  }

  return null;
}

export async function request(path, options = {}) {
  const {
    method = "GET",
    data,
    headers = {},
    timeout = 10000
  } = options;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);
  const traceId = createTraceId();
  const isFormData = typeof FormData !== "undefined" && data instanceof FormData;
  const requestHeaders = buildHeaders(headers);

  if (isFormData) {
    delete requestHeaders["Content-Type"];
  }

  try {
    const response = await fetch(resolveUrl(path), {
      method,
      headers: requestHeaders,
      body: data ? (isFormData ? data : JSON.stringify(data)) : undefined,
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    const responseData = await response.json().catch(() => ({}));
    if (!response.ok) {
      // 401 未授权 - 清除会话
      if (response.status === 401) {
        const { clearSession } = await import("./session-service.js");
        clearSession();
      }
      return {
        ok: false,
        code: responseData.code || `HTTP_${response.status}`,
        message: responseData.message || "请求失败",
        details: responseData.details,
        traceId,
        status: response.status
      };
    }

    const apiError = resolveApiError(responseData);
    if (apiError) {
      return {
        ok: false,
        code: apiError.code,
        message: apiError.message,
        traceId,
        status: response.status,
        details: responseData.details
      };
    }

    return {
      ok: true,
      data: responseData.data ?? responseData,
      traceId
    };
  } catch (error) {
    clearTimeout(timeoutId);
    return {
      ...mapFetchError(error),
      traceId
    };
  }
}
